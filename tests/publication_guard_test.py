"""Publication boundaries, including encoding and renamed-content variants."""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
from check_publication import inspect


class PublicationGuardTest(unittest.TestCase):
    def test_environment_and_private_directory_variants(self):
        for path in ['deploy/runtime.env', 'config/RUNTIME.ENV', 'config/.ENV.production', '.LOCAL/runtime.json',
                     'nested/EVIDENCE/file.txt', 'nested/database/HISTORY/snapshot.sql', r'nested\.IDEA\misc.xml']:
            with self.subTest(path=path): self.assertTrue(inspect(path, b'{}'))

    def test_wide_encodings_do_not_hide_credentials(self):
        secret = 'synthetic-guard-value-123456'
        for encoding in ['utf-8', 'utf-16', 'utf-16-le', 'utf-16-be', 'utf-32']:
            with self.subTest(encoding=encoding):
                self.assertTrue(inspect('settings.txt', ('password='+secret).encode(encoding), {secret}))

    def test_nul_or_late_secret_is_not_skipped(self):
        secret = 'synthetic-guard-value-123456'
        self.assertTrue(inspect('settings.txt', b'x'*9000+b'\0'+secret.encode(), {secret}))
        self.assertTrue(inspect('settings.txt', b'x'*9000+secret.encode(), {secret}))

    def test_new_literal_config_credentials_are_reviewed(self):
        self.assertTrue(inspect('config.txt', b'HW_DB_PASSWORD=new-unlisted-sensitive-value'))
        self.assertTrue(inspect('config.yaml', b'password: new-unlisted-sensitive-value'))

    def test_renamed_sql_and_archives_are_reviewed(self):
        sql = b"INSERT INTO sys_user(username,password_hash) VALUES ('fixture','fixture');"
        for name in ['snapshot.sql', 'snapshot.txt', 'snapshot.dat']:
            with self.subTest(name=name): self.assertTrue(inspect(name, sql))
        for name in ['report.docx', 'snapshot.zip', 'unknown.dat', 'frontend/public/assets/renamed.png']:
            with self.subTest(name=name): self.assertTrue(inspect(name, b'PK\x03\x04\0\xff'))

    def test_templates_code_and_public_schema_remain_valid(self):
        for path, data in [('deploy/runtime.env.example', b'HW_DB_PASSWORD=REPLACE_ME_LONG_SECRET'),
                           ('backend/config.yaml', b'password: ${HW_DB_PASSWORD}'),
                           ('frontend/src/client.ts', b'const token = makeToken();'),
                           ('database/schema.sql', b'CREATE TABLE sys_user(id bigint);'),
                           ('database/seed.sql', b"INSERT INTO literature_work(title) VALUES ('fixture');"),
                           ('.github/workflows/test.yml', b'MYSQL_ALLOW_EMPTY_PASSWORD: yes')]:
            with self.subTest(path=path): self.assertFalse(inspect(path, data))

    def test_only_expected_binary_asset_locations_and_headers_are_accepted(self):
        self.assertFalse(inspect('frontend/public/assets/font.woff2', b'wOF2\0\xff'))
        self.assertFalse(inspect('docs/images/sample.png', b'\x89PNG\r\n\x1a\n\0\xff'))
        self.assertTrue(inspect('notes/font.woff2', b'wOF2\0\xff'))


if __name__ == '__main__': unittest.main()
