"""Private SQL and immutable resource backup. Does not overwrite or delete backups."""
import argparse, datetime, hashlib, json, os, subprocess, tempfile, uuid, zipfile
from pathlib import Path

def digest(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda:f.read(1024*1024),b''): h.update(chunk)
    return h.hexdigest()

def verify(path):
    with zipfile.ZipFile(path) as z:
        if z.testzip() is not None: raise ValueError('Damaged backup ZIP')
        manifest=json.loads(z.read('manifest.json'))
        expected={'manifest.json',*(r['path'] for r in manifest['files'])}
        if set(z.namelist())!=expected or len(z.namelist())!=len(expected): raise ValueError('Unexpected or duplicate ZIP entries')
        for r in manifest['files']:
            p=Path(r['path'])
            if p.is_absolute() or '..' in p.parts or '\\' in r['path']: raise ValueError('Unsafe backup entry')
            h=hashlib.sha256();size=0
            with z.open(r['path']) as f:
                for chunk in iter(lambda:f.read(1024*1024),b''):h.update(chunk);size+=len(chunk)
            if h.hexdigest()!=r['sha256'] or size!=r['bytes']: raise ValueError('Backup hash mismatch')
        if 'database.sql' not in expected: raise ValueError('SQL snapshot missing')
        return manifest

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verify',type=Path)
    parser.add_argument('--client-file',type=Path)
    parser.add_argument('--mysql-dir',type=Path,default=Path('.'))
    parser.add_argument('--storage',type=Path)
    parser.add_argument('--destination',type=Path)
    args=parser.parse_args()
    if args.verify:
        manifest=verify(args.verify);print(json.dumps({'result':'PASS','files':len(manifest['files']),'at':manifest['at']},ensure_ascii=False));return
    if not args.client_file or not args.client_file.is_file() or not args.storage or not args.storage.is_dir() or not args.destination:
        parser.error('Backup requires an existing private client-file, storage directory and destination directory')
    os.umask(0o077);args.destination.mkdir(parents=True,exist_ok=True)
    mysql=args.mysql_dir/('mysql.exe' if os.name=='nt' else 'mysql')
    dump=args.mysql_dir/('mysqldump.exe' if os.name=='nt' else 'mysqldump')
    if str(args.mysql_dir)=='.': mysql='mysql';dump='mysqldump'
    query="SELECT storage_name,sha256,bytes FROM hw_course.course_resource"
    existence_sql="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='hw_assignment' AND table_name='assignment_attachment'"
    attachment_table=subprocess.run([str(mysql),'--defaults-file='+str(args.client_file.resolve()),'--batch','--skip-column-names','--execute='+existence_sql],capture_output=True,check=True).stdout.strip()
    if attachment_table==b'1':
        query+=" UNION ALL SELECT storage_name,sha256,bytes FROM hw_assignment.assignment_attachment"
    metadata=subprocess.run([str(mysql),'--defaults-file='+str(args.client_file.resolve()),'--batch','--skip-column-names','--execute='+query],capture_output=True,check=True).stdout.decode('utf8')
    for line in metadata.splitlines():
        name,sha,size=line.split('\t');p=args.storage/name
        if p.resolve().parent!=args.storage.resolve() or p.is_symlink() or not p.is_file() or digest(p)!=sha or p.stat().st_size!=int(size):
            raise ValueError('A recorded resource is missing or altered; backup stopped')
    at=datetime.datetime.now().astimezone().isoformat()
    final=args.destination/('hanwen-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S')+'-'+uuid.uuid4().hex[:6]+'.zip')
    with tempfile.TemporaryDirectory(prefix='hanwen-backup-',dir=args.destination) as temp:
        sql=Path(temp)/'database.sql'
        subprocess.run([str(dump),'--defaults-file='+str(args.client_file.resolve()),'--single-transaction','--no-tablespaces','--set-gtid-purged=OFF','--column-statistics=0','--result-file='+str(sql),'--databases','hw_user','hw_course','hw_learning','hw_assignment'],capture_output=True,check=True)
        files=[('database.sql',sql)]
        for p in args.storage.iterdir():
            if p.is_symlink() or not p.is_file():raise ValueError('Storage must contain regular resource files only')
            files.append(('uploads/'+p.name,p))
        manifest={'format':1,'at':at,'scope':'four databases and complete immutable upload directory','keyBackupRequiredSeparately':True,'files':[{'path':n,'bytes':p.stat().st_size,'sha256':digest(p)} for n,p in files]}
        partial=Path(temp)/'complete.zip'
        with zipfile.ZipFile(partial,'x',zipfile.ZIP_DEFLATED) as z:
            for n,p in files:z.write(p,n)
            z.writestr('manifest.json',json.dumps(manifest,ensure_ascii=False,indent=2))
        verify(partial)
        # Exclusive creation prevents a backup from silently replacing an older artifact.
        with final.open('xb') as dest,partial.open('rb') as source:
            for chunk in iter(lambda:source.read(1024*1024),b''):dest.write(chunk)
    print(json.dumps({'result':'PASS','backup':str(final),'files':len(files),'sha256':digest(final)},ensure_ascii=False))

if __name__=='__main__':main()
