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


def inspect(path, content, known_secrets=()):
    parts = PurePosixPath(path).parts
    reasons = []
    if any(p in {'.local', '.idea', 'node_modules', 'target', '.playwright-cli'} for p in parts):
        reasons.append('private or generated directory')
    if parts[0] in {'output', 'evidence'} or path.startswith('database/history/'):
        reasons.append('local evidence or database history')
    filename = parts[-1].lower()
    if filename.startswith('.env') and not filename.endswith('.example'):
        reasons.append('private environment file')
    if PurePosixPath(filename).suffix in {'.pem', '.key', '.p12', '.pfx', '.jks', '.zip', '.rar', '.7z', '.bak', '.dump'}:
        reasons.append('key, archive or backup')
    if filename.endswith('.sql') and '-data' in filename:
        reasons.append('database data snapshot')
    if len(content) > 10 * 1024 * 1024:
        reasons.append('large file requires separate review')
    # Binary web assets are checked by path/size; text is checked for sensitive values.
    if b'\0' not in content[:8192]:
        text = content.decode('utf-8', errors='replace')
        reasons.extend(label for label, pattern in PATTERNS.items() if pattern.search(text))
        if any(secret in text for secret in known_secrets):
            reasons.append('value from private local credentials')
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
