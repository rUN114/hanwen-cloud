"""Read-only service checks. Emits local JSON and nonzero exit status for unhealthy services."""
import argparse, datetime, json, shutil, urllib.request
from pathlib import Path

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--directory',type=Path,default=Path(__file__).resolve().parents[1])
    p.add_argument('--output',type=Path)
    p.add_argument('--frontend-url',default='http://127.0.0.1:5173')
    p.add_argument('--gateway-port',type=int,default=8080)
    args=p.parse_args();rows=[]
    for port in (8761,args.gateway_port,8101,8102,8103,8104,8105):
        try:
            with urllib.request.urlopen(f'http://127.0.0.1:{port}/actuator/health',timeout=5) as r:
                ok=r.status==200 and json.load(r).get('status')=='UP'
        except Exception:ok=False
        rows.append({'servicePort':port,'result':'PASS' if ok else 'FAIL'})
    try:
        with urllib.request.urlopen(args.frontend_url,timeout=8) as r:ok=r.status==200
    except Exception:ok=False
    rows.append({'check':'frontend','result':'PASS' if ok else 'FAIL'})
    disk=shutil.disk_usage(args.directory);free=disk.free/disk.total
    rows.append({'check':'disk','result':'FAIL' if disk.free<2*1024**3 else 'WARN' if free<.10 else 'PASS','freeBytes':disk.free,'freePercent':round(free*100,2)})
    result='FAIL' if any(x['result']=='FAIL' for x in rows) else 'WARN' if any(x['result']=='WARN' for x in rows) else 'PASS'
    data={'at':datetime.datetime.now().astimezone().isoformat(),'checks':rows,'result':result}
    if args.output:
        args.output.parent.mkdir(parents=True,exist_ok=True)
        tmp=args.output.with_suffix('.tmp');tmp.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf8');tmp.replace(args.output)
    print(json.dumps(data,ensure_ascii=False));return 1 if data['result']=='FAIL' else 0

if __name__=='__main__':raise SystemExit(main())
