"""One-shot health collection with local issue/recovery history. Does not send messages."""
import argparse,datetime as dt,json,subprocess,sys
from pathlib import Path

def transitions(previous,snapshot):
    states={};events=[]
    for row in snapshot['checks']:
        key='port:'+str(row['servicePort']) if 'servicePort' in row else row['check']
        status=row['result'];old=previous.get(key)
        if status not in ('PASS','WARN','FAIL'):raise ValueError('Unexpected check result')
        since=old.get('since') if old and old['result']==status else snapshot['at']
        states[key]={'result':status,'since':since}
        if (old is None and status!='PASS') or old is not None and old['result']!=status:
            elapsed=None
            if old:
                elapsed=max(0,round((dt.datetime.fromisoformat(snapshot['at'])-dt.datetime.fromisoformat(old['since'])).total_seconds(),3))
            events.append({'at':snapshot['at'],'check':key,'from':old['result'] if old else 'UNKNOWN','to':status,'kind':'RECOVERED' if status=='PASS' else 'WARNING' if status=='WARN' else 'INCIDENT','previousStateSeconds':elapsed})
    return states,events

def save(path,value):
    tmp=path.with_suffix('.tmp');tmp.write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf8');tmp.replace(path)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--history-dir',type=Path,default=Path(__file__).resolve().parents[1]/'.local/monitor');p.add_argument('--directory',type=Path,default=Path(__file__).resolve().parents[1]);p.add_argument('--gateway-port',type=int,default=8080);p.add_argument('--frontend-url',default='http://127.0.0.1:5173');args=p.parse_args()
    args.history_dir.mkdir(parents=True,exist_ok=True);lock=args.history_dir/'collect.lock'
    try:handle=lock.open('x')
    except FileExistsError:raise SystemExit('A collector is already running; inspect the lock after an interrupted run.')
    try:
        with handle:handle.write(str(__import__('os').getpid()))
        collected=subprocess.run([sys.executable,str(Path(__file__).with_name('healthcheck.py')),'--directory',str(args.directory),'--gateway-port',str(args.gateway_port),'--frontend-url',args.frontend_url],capture_output=True,text=True,timeout=90)
        if collected.returncode not in (0,1):raise ValueError('Health collector failed')
        snapshot=json.loads(collected.stdout);statefile=args.history_dir/'state.json';previous=json.loads(statefile.read_text()) if statefile.exists() else {}
        states,events=transitions(previous,snapshot)
        for name,rows in [('history.jsonl',[snapshot]),('events.jsonl',events)]:
            if rows:
                path=args.history_dir/name
                # Rotate by size without deleting prior evidence.
                if path.exists() and path.stat().st_size>5*1024*1024:path.rename(args.history_dir/(path.stem+'-'+dt.datetime.now().strftime('%Y%m%d-%H%M%S-%f')+'.jsonl'))
                with path.open('a',encoding='utf8') as stream:
                    for row in rows:stream.write(json.dumps(row,ensure_ascii=False)+'\n')
        save(statefile,states);save(args.history_dir/'latest.json',snapshot)
        print(json.dumps({'result':snapshot['result'],'newEvents':len(events),'checks':len(snapshot['checks']),'at':snapshot['at']}));return 1 if snapshot['result']=='FAIL' else 0
    finally:lock.unlink(missing_ok=True)

if __name__=='__main__':raise SystemExit(main())
