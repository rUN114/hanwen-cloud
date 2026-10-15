"""Monitor transition rules only; live fault/recovery is checked separately."""
import importlib.util,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('monitor',Path(__file__).resolve().parents[1]/'tools/monitor.py');monitor=importlib.util.module_from_spec(spec);spec.loader.exec_module(monitor)
def snapshot(at,status):return {'at':at,'checks':[{'servicePort':8103,'result':status}]}
class MonitorTest(unittest.TestCase):
 def test_initial_pass_is_quiet(self):self.assertEqual([],monitor.transitions({},snapshot('2026-10-12T10:00:00+08:00','PASS'))[1])
 def test_initial_fail_emits_incident(self):self.assertEqual('INCIDENT',monitor.transitions({},snapshot('2026-10-12T10:00:00+08:00','FAIL'))[1][0]['kind'])
 def test_same_failure_is_quiet_and_keeps_start(self):
  state,_=monitor.transitions({},snapshot('2026-10-12T10:00:00+08:00','FAIL'));new,events=monitor.transitions(state,snapshot('2026-10-12T10:02:00+08:00','FAIL'));self.assertEqual([],events);self.assertEqual(state,new)
 def test_recovery_records_duration(self):
  state,_=monitor.transitions({},snapshot('2026-10-12T10:00:00+08:00','FAIL'));_,events=monitor.transitions(state,snapshot('2026-10-12T10:02:00+08:00','PASS'));self.assertEqual('RECOVERED',events[0]['kind']);self.assertEqual(120,events[0]['previousStateSeconds'])
 def test_warning_worsens_then_recovers(self):
  state,_=monitor.transitions({},snapshot('2026-10-12T10:00:00+08:00','WARN'));state,events=monitor.transitions(state,snapshot('2026-10-12T10:01:00+08:00','FAIL'));self.assertEqual('INCIDENT',events[0]['kind']);_,events=monitor.transitions(state,snapshot('2026-10-12T10:02:00+08:00','PASS'));self.assertEqual('RECOVERED',events[0]['kind'])
if __name__=='__main__':unittest.main()
