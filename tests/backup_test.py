"""Backup integrity and Python/Java maintenance-lock regression checks."""
import io
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import backup


class BackupTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.storage = self.root / 'uploads'
        self.storage.mkdir()
        self.client = self.root / 'client.ini'
        self.client.write_text('[client]\n', encoding='utf-8')
        self.args = SimpleNamespace(storage=self.storage, destination=self.root / 'backups',
                                    client_file=self.client, mysql_dir=Path('.'))
        self.args.destination.mkdir()
        self.active = self.storage / 'active.bin'
        self.active.write_bytes(b'original bytes')

    def database(self, args, **kwargs):
        query = next((a for a in args if a.startswith('--execute=')), '')
        if 'information_schema.tables' in query:
            return SimpleNamespace(stdout=b'1')
        if query:
            self.assertEqual(2, query.count("status='ACTIVE' AND storage_name IS NOT NULL"))
            row = f'active.bin\t{backup.digest(self.active)}\t{self.active.stat().st_size}\n'
            return SimpleNamespace(stdout=row.encode())
        output = next(a.split('=', 1)[1] for a in args if a.startswith('--result-file='))
        Path(output).write_text('-- isolated SQL snapshot\n', encoding='utf-8')
        return SimpleNamespace(stdout=b'')

    def test_backup_excludes_lock_and_tolerates_physically_purged_records(self):
        with patch.object(backup.subprocess, 'run', side_effect=self.database), patch('sys.stdout', new=io.StringIO()):
            with backup.exclusive_storage(self.storage):
                backup.create_backup(self.args)
        path = next(self.args.destination.glob('*.zip'))
        manifest = backup.verify(path)
        self.assertEqual({'database.sql', 'uploads/active.bin'}, {r['path'] for r in manifest['files']})
        with zipfile.ZipFile(path) as archive:
            self.assertEqual(b'original bytes', archive.read('uploads/active.bin'))
        self.assertTrue((self.storage / backup.LOCK_FILE).exists())

    def test_missing_active_file_still_stops_backup(self):
        def missing(args, **kwargs):
            if any('information_schema.tables' in a for a in args): return SimpleNamespace(stdout=b'1')
            return SimpleNamespace(stdout=b'missing.bin\tunknown\t1\n')
        with patch.object(backup.subprocess, 'run', side_effect=missing):
            with self.assertRaisesRegex(ValueError, 'missing or altered'):
                with backup.exclusive_storage(self.storage): backup.create_backup(self.args)
        self.assertEqual([], list(self.args.destination.glob('*.zip')))

    def check_contender(self, command):
        with subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True) as child:
            try:
                self.assertEqual('LOCKED', child.stdout.readline().strip())
                with self.assertRaisesRegex(RuntimeError, 'maintenance is busy'):
                    with backup.exclusive_storage(self.storage): self.fail('Competing maintenance ran')
            finally:
                child.communicate(input='\n', timeout=15)
            self.assertEqual(0, child.returncode)
        with backup.exclusive_storage(self.storage): pass

    def test_other_python_process_is_excluded(self):
        code = "from pathlib import Path; import sys; sys.path.insert(0,sys.argv[1]); import backup\nwith backup.exclusive_storage(Path(sys.argv[2])):\n print('LOCKED',flush=True)\n sys.stdin.read(1)\n"
        self.check_contender([sys.executable, '-c', code, str(ROOT / 'tools'), str(self.storage)])

    def test_java_file_channel_uses_the_same_byte_range_lock(self):
        if os.environ.get('JAVA_HOME'):
            java = Path(os.environ['JAVA_HOME']) / 'bin' / ('java.exe' if os.name == 'nt' else 'java')
        else:
            java = shutil.which('java')
        self.assertTrue(java, 'Java is required for the cross-language lock check')
        classes = ROOT / 'backend/common/target/classes'
        self.assertTrue((classes / 'org/hanwen/common/StorageMaintenance.class').exists(), 'Build common before this check')
        source = self.root / 'LockProbe.java'
        source.write_text('''import java.nio.file.Path;
import org.hanwen.common.StorageMaintenance;
class LockProbe {
 public static void main(String[] args) throws Exception {
  if (!StorageMaintenance.tryRunExclusive(Path.of(args[0]), () -> {
   System.out.println("LOCKED"); System.out.flush();
   try { System.in.read(); } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
  })) System.exit(2);
 }
}''', encoding='utf-8')
        self.check_contender([str(java), '--class-path', str(classes), str(source), str(self.storage)])


if __name__ == '__main__': unittest.main()
