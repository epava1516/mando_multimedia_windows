import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import unittest
from types import SimpleNamespace
from unittest.mock import Mock
from diagnostics import Events, desktop_status

class DiagnosticTests(unittest.TestCase):
    def test_event_whitelist_and_bounds(self):
        events=Events()
        with self.assertRaises(ValueError):events.add('password=secret')
        for _ in range(1000):events.add('INPUT_REJECTED')
        self.assertEqual(len(events.snapshot()),24)
        snapshot=events.snapshot();snapshot[0]['code']='bad'
        self.assertEqual(events.snapshot()[0]['code'],'INPUT_REJECTED')

    def test_untrusted_broker_fields_are_not_forwarded(self):
        bridge=Mock()
        bridge.request.return_value={'ok':True,'locked':False,'worker_running':True,'worker_verified':True,
            'last_stage':'password=secret','last_win32':'private text','token':'secret','extra':'secret'}
        result=desktop_status(SimpleNamespace(desktop_bridge=bridge))
        self.assertEqual(result['last_stage'],'REQUEST');self.assertEqual(result['last_win32'],0)
        self.assertNotIn('secret',str(result));self.assertTrue(result['service_reachable'])

    def test_missing_and_failed_broker(self):
        self.assertFalse(desktop_status(object())['service_reachable'])
        bridge=Mock(stage='PIPE_OPEN');bridge.request.side_effect=OSError('C:/private/secret')
        result=desktop_status(SimpleNamespace(desktop_bridge=bridge))
        self.assertFalse(result['service_reachable']);self.assertEqual(result['probe'],'PIPE_OPEN')
        self.assertNotIn('secret',str(result))

    def test_old_broker_reports_unknown_worker_instead_of_ready(self):
        bridge=Mock();bridge.request.return_value={'ok':True,'available':True,'locked':True}
        result=desktop_status(SimpleNamespace(desktop_bridge=bridge))
        self.assertTrue(result['service_reachable']);self.assertFalse(result['worker_verified'])
