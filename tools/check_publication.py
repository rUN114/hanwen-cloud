"""Inspect the Git index before publishing; never print matched secret values."""
import argparse
import io
import json
from pathlib import Path, PurePosixPath
import re
import subprocess


PATTERNS = {
    'private key': re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
    'GitHub token': re.compile(r'\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,})\b'),
    'API key': re.compile(r'\bsk-[A-Za-z0-9_-]{30,}\b'),
    'local user directory': re.compile(r'[A-Za-z]:[\\/]+Users[\\/]', re.I),
    'student identifier': re.compile(r'\b20\d{9}\b'),
}

def decode_text(content):
    if content.startswith((b'\xff\xfe\x00\x00', b'\x00\x00\xfe\xff')):
        return content.decode('utf-32')
    if content.startswith((b'\xff\xfe', b'\xfe\xff')):
        return content.decode('utf-16')
    if b'\0' in content:
        # 无BOM的宽字符文本也必须检查，不能按二进制略过。
        if content[1::2].count(0) > len(content) // 5:
            return content.decode('utf-16-le')
        if content[0::2].count(0) > len(content) // 5:
            return content.decode('utf-16-be')
        raise UnicodeError('Not a supported text encoding')
    return content.decode('utf-8-sig')


def is_web_asset(path, content):
    if not (path.startswith('frontend/public/') or path.startswith('docs/images/')):
        return False
    suffix = PurePosixPath(path).suffix
    signatures = {'.png': (b'\x89PNG\r\n\x1a\n',), '.jpg': (b'\xff\xd8\xff',), '.jpeg': (b'\xff\xd8\xff',),
                  '.gif': (b'GIF87a', b'GIF89a'), '.ico': (b'\0\0\1\0',),
                  '.woff2': (b'wOF2',), '.woff': (b'wOFF',), '.ttf': (b'\0\1\0\0',), '.otf': (b'OTTO',)}
    if suffix in signatures:
        return content.startswith(signatures[suffix])
    return suffix == '.webp' and content.startswith(b'RIFF') and content[8:12] == b'WEBP'


def inspect(path, content, known_secrets=()):
    normalized = path.replace('\\', '/').lower()
    parts = PurePosixPath(normalized).parts
    reasons = []
    if any(p in {'.local', '.idea', 'node_modules', 'target', '.playwright-cli'} for p in parts):
        reasons.append('private or generated directory')
    if any(p in {'output', 'evidence'} for p in parts) or 'database/history/' in normalized:
        reasons.append('local evidence or database history')
    filename = parts[-1].lower()
    if (filename.startswith('.env') or '.env' in PurePosixPath(filename).suffixes) and not filename.endswith('.example'):
        reasons.append('private environment file')
    if PurePosixPath(filename).suffix in {'.pem', '.key', '.p12', '.pfx', '.jks', '.zip', '.rar', '.7z', '.bak', '.dump', '.gz', '.bz2', '.xz', '.tar', '.docx', '.xlsx', '.pptx'}:
        reasons.append('key, archive or backup')
    if filename.endswith('.sql') and '-data' in filename:
        reasons.append('database data snapshot')
    if len(content) > 10 * 1024 * 1024:
        reasons.append('large file requires separate review')
        return reasons
    if is_web_asset(normalized, content):
        return reasons
    try:
        text = decode_text(content)
    except UnicodeError:
        reasons.append('unreviewed binary or invalid text encoding')
    else:
        reasons.extend(label for label, pattern in PATTERNS.items() if pattern.search(text))
        if any(secret in text for secret in known_secrets):
            reasons.append('value from private local credentials')
        # 只识别实际配置行，不把测试源码里的字符串或环境变量引用当作凭据。
        source_suffixes = {'.py', '.java', '.ts', '.vue', '.js', '.mjs', '.css', '.html', '.ps1', '.sh', '.cmd', '.bat', '.xml', '.md'}
        assignments = [] if PurePosixPath(filename).suffix in source_suffixes else re.findall(r'''(?im)^\s*["']?([\w.-]*(?:password|secret|token|service[_-]?key)[\w.-]*)["']?\s*[:=]\s*["']?([^\s"',}]+)''', text)
        for _, value in assignments:
            placeholder = value.upper().startswith(('CHANGE_ME', 'REPLACE_ME', 'REPLACE_WITH', 'YOUR_'))
            if value and not value.startswith(('$', '<')) and not placeholder and value.upper() not in {'NONE', 'NULL', 'FALSE', 'TRUE', 'YES', 'NO'}:
                reasons.append('literal credential configuration requires review')
                break
        sensitive_tables = r'(?:sys_user|auth_refresh_session|course_enrollment|learning_progress|assignment_submission|assignment_grade)'
        sql_text = PurePosixPath(filename).suffix == '.sql' or re.match(r'(?is)^\s*(?:(?:--[^\n]*\n)|\s)*(?:INSERT|REPLACE|USE|SET|CREATE)\b', text)
        if text.lstrip().startswith('-- MySQL dump') or (sql_text and re.search(r'(?i)\bINSERT\s+(?:IGNORE\s+)?INTO\s+(?:`?hw_\w+`?\.)?`?' + sensitive_tables + r'`?\s*\(', text)):
            reasons.append('database data snapshot content')
    return reasons


def local_secrets(root):
    found = set()

    def visit(value, key=''):
        if isinstance(value, dict):
            for name, child in value.items():
                visit(child, name)
        elif isinstance(value, list):
            for child in value:
                visit(child, key)
        elif isinstance(value, str) and len(value) >= 12 and re.search(r'password|token|service.?key|secret', key, re.I):
            found.add(value)

    for path in (root / '.local').glob('*.json'):
        try:
            visit(json.loads(path.read_text(encoding='utf-8-sig')))
        except (OSError, ValueError):
            continue
    return found


def index_files(root):
    entries = subprocess.check_output(['git', 'ls-files', '--stage', '-z'], cwd=root).split(b'\0')
    files = []
    for entry in filter(None, entries):
        metadata, path = entry.split(b'\t', 1)
        mode, oid, stage = metadata.split()
        if stage != b'0' or mode not in {b'100644', b'100755'}:
            raise ValueError('Unmerged or non-regular index entry requires manual review')
        files.append((path.decode('utf-8'), oid))
    if not files:
        raise ValueError('No staged/tracked files; stage the intended files first')
    data = subprocess.check_output(['git', 'cat-file', '--batch'], input=b''.join(oid + b'\n' for _, oid in files), cwd=root)
    stream = io.BytesIO(data)
    for path, _ in files:
        header = stream.readline().split()
        if len(header) != 3 or header[1] != b'blob':
            raise ValueError('Unexpected Git object')
        content = stream.read(int(header[2]))
        if stream.read(1) != b'\n':
            raise ValueError('Invalid Git batch object boundary')
        yield path, content


def self_test():
    assert inspect('.local/runtime.json', b'{}')
    assert inspect('database/hanwen-data.sql', b'')
    assert inspect('config.txt', ('-----BEGIN ' + 'PRIVATE KEY-----').encode())
    assert inspect('config.txt', b'password=fixture-value-123', {'fixture-value-123'})
    assert not inspect('deploy/runtime.env.example', b'HW_DB_PASSWORD=CHANGE_ME')
    assert not inspect('frontend/src/main.ts', b'import App from "./App.vue";')
    print('Publication guard: 6 self-checks passed')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--self-test', action='store_true')
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return 0
    root = Path(__file__).resolve().parents[1]
    known = local_secrets(root)
    failures, count = [], 0
    for path, content in index_files(root):
        count += 1
        reasons = inspect(path, content, known)
        if reasons:
            failures.append((path, reasons))
    for path, reasons in failures:
        print(path + ': ' + ', '.join(reasons))
    print(f'Publication guard: {count} index files; {len(failures)} blocked files')
    return 1 if failures else 0


if __name__ == '__main__':
    raise SystemExit(main())
