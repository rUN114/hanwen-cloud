"""Destructive checks restricted to the explicitly selected isolated MySQL instance."""
import argparse
import difflib
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import backup

DATABASES = ('hw_user', 'hw_course', 'hw_learning', 'hw_assignment')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mysql-container')
    parser.add_argument('--mysql-dir', type=Path)
    parser.add_argument('--data-dir', required=True)
    args = parser.parse_args()
    if args.mysql_container:
        command = ['docker', 'exec', '-i', args.mysql_container, 'mysql', '-uroot']
        expected_port = '3306'
    else:
        if not args.mysql_dir: parser.error('--mysql-dir is required outside CI')
        command = [str(args.mysql_dir / ('mysql.exe' if os.name == 'nt' else 'mysql')),
                   '--no-defaults', '--host=127.0.0.1', '--port=14327', '--user=root']
        expected_port = '14327'
    command += ['--default-character-set=utf8mb4', '--batch', '--raw', '--skip-column-names']

    def sql(text):
        result = subprocess.run(command, input=text.encode('utf-8'), capture_output=True)
        if result.returncode: raise RuntimeError(result.stderr.decode('utf-8', errors='replace'))
        return result.stdout.decode('utf-8').strip()

    def isolated():
        port, directory = sql('SELECT @@port,@@datadir;').split('\t')
        if port != expected_port or Path(directory).resolve() != Path(args.data_dir).resolve():
            raise RuntimeError('Refusing to reset a database outside the explicit isolated instance')

    def reset():
        isolated()
        sql(''.join('DROP DATABASE IF EXISTS '+name+';' for name in DATABASES))

    reset()
    for name in ('user', 'course', 'learning', 'assignment'):
        sql((ROOT / 'database' / (name+'.sql')).read_text(encoding='utf-8'))
    signature_sql = "SELECT table_schema,table_name,column_name,column_type,is_nullable,COALESCE(column_default,'<NULL>'),extra FROM information_schema.columns WHERE table_schema IN ('hw_user','hw_course','hw_learning','hw_assignment') ORDER BY table_schema,table_name,ordinal_position;"
    baseline = sql(signature_sql)
    with tempfile.TemporaryDirectory(prefix='hanwen-schema-check-') as directory:
        work = Path(directory)
        storage = work / 'uploads'
        storage.mkdir()
        payload = b'isolated immutable upload'
        sha = hashlib.sha256(payload).hexdigest()
        for name in ('active-course.bin', 'active-attachment.bin', 'removed-course.bin', 'removed-attachment.bin'):
            (storage / name).write_bytes(payload)
        sql("""USE hw_course;
INSERT INTO course(id,teacher_id,name,summary,category,status,created_at,updated_at) VALUES(9001,1,'schema fixture','','古文','DRAFT',NOW(),NOW());
INSERT INTO course_chapter(id,course_id,title,content,sort_no,created_at,updated_at) VALUES(9001,9001,'章节','正文',1,NOW(),NOW());
INSERT INTO chapter_quiz(id,course_id,chapter_id,title,instructions,status,version,created_at,updated_at) VALUES(9001,9001,9001,'练习','','DRAFT',0,NOW(),NOW());
INSERT INTO quiz_question(id,quiz_id,prompt,option_a,option_b,option_c,option_d,correct_option,explanation,sort_no,question_type,points) VALUES(9001,9001,'书面作答','','','','','超过四个字的中文答案','',1,'SHORT_ANSWER',10);
USE hw_assignment;
INSERT INTO assignment_task(id,course_id,teacher_id,title,description,max_score,status,created_at,updated_at) VALUES(9001,9001,1,'fixture','',100,'PUBLISHED',NOW(),NOW());
""")
        for database, table, names in [('hw_course', 'course_resource', ('active-course.bin', 'removed-course.bin')),
                                       ('hw_assignment', 'assignment_attachment', ('active-attachment.bin', 'removed-attachment.bin'))]:
            for index, name in enumerate(names):
                status = 'ACTIVE' if index == 0 else 'REMOVED'
                columns = 'course_id,title,description' if table == 'course_resource' else 'assignment_id,student_id'
                values = "9001,'资源',''" if table == 'course_resource' else '9001,3'
                sql(f"INSERT INTO {database}.{table}(id,{columns},original_name,mime_type,bytes,sha256,storage_name,status,created_at,removed_at) VALUES({9001+index},{values},'fixture.txt','text/plain',{len(payload)},'{sha}','{name}','{status}',DATE_SUB(NOW(),INTERVAL 60 DAY),NULL);")
        # 已清盘记录应保留在SQL中，不能再阻止完整备份。
        sql(f"INSERT INTO hw_assignment.assignment_attachment(id,assignment_id,student_id,original_name,mime_type,bytes,sha256,storage_name,status,created_at,removed_at) VALUES(9003,9001,3,'purged.txt','text/plain',1,'{sha}',NULL,'REMOVED',NOW(),DATE_SUB(NOW(),INTERVAL 31 DAY));")
        migrations = sorted((ROOT / 'database/migrations').glob('V*__*.sql'), key=lambda p: int(p.name.split('__')[0][1:]))
        first_removal = None
        for replay in range(2):
            for path in migrations:
                try: sql(path.read_text(encoding='utf-8'))
                except RuntimeError as error: raise RuntimeError(f'{path.name}, replay {replay+1}: {error}') from error
            signature = sql(signature_sql)
            if signature != baseline:
                difference = '\n'.join(difflib.unified_diff(baseline.splitlines(), signature.splitlines(), fromfile='fresh', tofile='replayed', n=1))
                raise AssertionError('Migration replay changed the fresh-schema column contract:\n'+difference)
            assert sql('SELECT correct_option FROM hw_course.quiz_question WHERE id=9001;') == '超过四个字的中文答案'
            removal = sql('SELECT removed_at FROM hw_course.course_resource WHERE id=9002;')
            assert removal != 'NULL'
            if first_removal is not None: assert first_removal == removal
            first_removal = removal
        client = work / 'client.ini'
        client.write_text('[client]\nhost=127.0.0.1\nport=14327\nuser=root\ndefault-character-set=utf8mb4\n', encoding='utf-8')
        binary = args.mysql_dir
        if args.mysql_container:
            binary = work / 'bin'
            binary.mkdir()
            # 将现有备份工具的两条CLI调用透明转到本次CI容器，SQL仍由真实MySQL执行。
            proxy = '''#!/usr/bin/env python3
import pathlib,subprocess,sys
name=pathlib.Path(sys.argv[0]).name
args=[a for a in sys.argv[1:] if not a.startswith('--defaults-file=')]
output=next((a.split('=',1)[1] for a in args if a.startswith('--result-file=')),None)
args=[a for a in args if not a.startswith('--result-file=')]
p=subprocess.run(['docker','exec','-i',CONTAINER,name,'-uroot']+args,capture_output=True)
if output and p.returncode==0:pathlib.Path(output).write_bytes(p.stdout)
else:sys.stdout.buffer.write(p.stdout)
sys.stderr.buffer.write(p.stderr)
sys.exit(p.returncode)
'''.replace('CONTAINER', repr(args.mysql_container))
            for name in ('mysql', 'mysqldump'):
                path = binary / name
                path.write_text(proxy, encoding='utf-8')
                path.chmod(0o700)
        options = argparse.Namespace(storage=storage, destination=work/'backups', client_file=client, mysql_dir=binary)
        options.destination.mkdir()
        with backup.exclusive_storage(storage): backup.create_backup(options)
        archive = next(options.destination.glob('*.zip'))
        backup.verify(archive)
        with zipfile.ZipFile(archive) as z:
            snapshot = z.read('database.sql').decode('utf-8')
            restored = work / 'restored'
            restored.mkdir()
            for name in z.namelist():
                if name.startswith('uploads/'):
                    path = restored / name
                    path.parent.mkdir(exist_ok=True)
                    path.write_bytes(z.read(name))
        reset()
        sql(snapshot)
        assert sql('SELECT correct_option FROM hw_course.quiz_question WHERE id=9001;') == '超过四个字的中文答案'
        assert sql('SELECT COUNT(*) FROM hw_assignment.assignment_attachment WHERE id IN (9001,9002,9003);') == '3'
        for name in ('active-course.bin', 'active-attachment.bin'): assert (restored/'uploads'/name).read_bytes() == payload
        # 后续Java集成测试使用干净数据库；这里不保留备份样本数据。
        sql('DELETE FROM hw_assignment.assignment_attachment;DELETE FROM hw_assignment.assignment_task;DELETE FROM hw_course.quiz_question;DELETE FROM hw_course.chapter_quiz;DELETE FROM hw_course.course_resource;DELETE FROM hw_course.course_chapter;DELETE FROM hw_course.course;')
    print(json.dumps({'schemas':4,'migrations':len(migrations),'replays':2,'writtenAnswerPreserved':True,'backupRestore':'PASS'}))


if __name__ == '__main__': main()
