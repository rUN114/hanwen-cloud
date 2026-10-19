"""Catalog consistency and protection of existing content and immutable uploads."""
import importlib.util
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from fixture_guard import require_fixture_environment

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('catalog', ROOT / 'tools/install_academy_content.py')
catalog = importlib.util.module_from_spec(spec)
spec.loader.exec_module(catalog)


class ContentCatalogTest(unittest.TestCase):
    def setUp(self):
        self.works, self.courses = catalog.load_catalog()

    def test_every_book_belongs_to_a_reading_path(self):
        used = {key for course in self.courses for chapter in course['chapters'] for key in chapter['works']}
        self.assertEqual(used, {work['key'] for work in self.works})

    def test_existing_curriculum_is_refused_before_writing_uploads(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(catalog, 'mysql', return_value='1\t0\t0\t0') as query:
            storage = Path(directory)
            with self.assertRaisesRegex(ValueError, 'Existing teaching content'):
                catalog.install(Path('mysql'), Path('private.ini'), storage, self.works, self.courses)
            self.assertEqual(list(storage.iterdir()), [])
            self.assertEqual(query.call_count, 1)

    def test_missing_teacher_is_refused_before_writing_uploads(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(catalog, 'mysql', side_effect=['0\t0\t0\t0', '0']):
            storage = Path(directory)
            with self.assertRaisesRegex(ValueError, 'enabled teacher'):
                catalog.install(Path('mysql'), Path('private.ini'), storage, self.works, self.courses)
            self.assertEqual(list(storage.iterdir()), [])

    def test_sql_failure_keeps_preexisting_uploads_and_removes_only_new_files(self):
        lookup = {work['key']: work for work in self.works}
        guide = catalog.resource(self.courses[0], lookup)
        with tempfile.TemporaryDirectory() as directory, patch.object(
            catalog, 'mysql', side_effect=['0\t0\t0\t0', '1', '1', RuntimeError('database unavailable')]
        ):
            storage = Path(directory)
            existing = storage / guide['storage']
            existing.write_bytes(guide['data'])
            unrelated = storage / 'existing-student-resource.txt'
            unrelated.write_bytes(b'keep this upload')
            with self.assertRaisesRegex(RuntimeError, 'database unavailable'):
                catalog.install(Path('mysql'), Path('private.ini'), storage, self.works, self.courses)
            self.assertEqual(set(storage.iterdir()), {existing, unrelated})
            self.assertEqual(existing.read_bytes(), guide['data'])
            self.assertEqual(unrelated.read_bytes(), b'keep this upload')

    def test_fixture_writer_is_blocked_when_reading_profile_is_installed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            require_fixture_environment(root)
            (root / '.local').mkdir()
            (root / '.local/academy-content.json').write_text('{}', encoding='utf8')
            with self.assertRaisesRegex(RuntimeError, 'blocked here'):
                require_fixture_environment(root)


if __name__ == '__main__':
    unittest.main()
