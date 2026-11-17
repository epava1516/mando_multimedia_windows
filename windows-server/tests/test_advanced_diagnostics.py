import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import ctypes as C
import json
import struct
import threading
import time
import unittest
from unittest.mock import Mock,patch
from types import SimpleNamespace
from desktop_bridge import DesktopBridge,BridgeFailure
from diagnostics import advanced_status,clean_probe

class AdvancedTests(unittest.TestCase):
    def bridge(self, payload=None):
        b=DesktopBridge.__new__(DesktopBridge);b.lock=threading.RLock();b.last_probe={}
        b.connect=Mock();b.close=Mock();b.io=Mock(side_effect=lambda data=None,count=0:len(data))
        body=json.dumps(payload or {'ok':True,'available':True}).encode()
        b.read=Mock(side_effect=[struct.pack('!I',len(body)),body]);return b

    def test_separate_read_stage_and_timeout_without_exception_text(self):
        b=self.bridge();b.read.side_effect=TimeoutError('private secret password')
        with self.assertRaises(TimeoutError):b.request({'op':'status'},advanced=True)
        self.assertEqual(b.last_probe['kind'],'TIMEOUT');self.assertEqual(b.last_probe['stage'],'PIPE_READ_HEADER')
        self.assertNotIn('secret',str(b.last_probe));b.close.assert_called_once()
        self.assertGreater(b.deadline-b.started,1.9);self.assertLess(b.deadline-b.started,2.1)

    def test_native_error_retained_and_rejection_classified(self):
        b=self.bridge();b.io.side_effect=BridgeFailure('WIN32',109)
        with self.assertRaises(OSError):b.request({'op':'status'})
        self.assertEqual(b.last_probe['win32'],109);self.assertEqual(b.last_probe['stage'],'PIPE_WRITE')
        b=self.bridge({'ok':False,'reason':'CLIENT_SESSION_MISMATCH'})
        with self.assertRaises(OSError):b.request({'op':'status'},advanced=True)
        self.assertEqual(b.last_probe['kind'],'CLIENT_SESSION_MISMATCH')
        b=self.bridge({'ok':False,'reason':{'secret':'text'}})
        with self.assertRaises(OSError):b.request({'op':'status'})
        self.assertEqual(b.last_probe['kind'],'BROKER_REJECTED')

    def test_success_is_passive_and_does_not_send_input(self):
        b=self.bridge();b.request({'op':'status'},advanced=True)
        packet=b.io.call_args.kwargs['data']
        self.assertEqual(json.loads(packet[4:]),{'op':'status'})
        self.assertEqual(b.last_probe['kind'],'OK')
        self.assertEqual([x['stage'] for x in b.last_probe['trace']],['PIPE_WRITE','PIPE_READ_HEADER','PIPE_READ_BODY','PIPE_RESPONSE'])

    def test_advanced_whitelist_has_no_account_or_file_contents(self):
        bridge=Mock();bridge.advanced.return_value={'local':{'service_state':'RUNNING','service_account':'LOCAL_SYSTEM','allowed_sid_file':'OK',
            'account_match':False,'session_match':True,'username':'PRIVATE_SECRET','sid':'PRIVATE_SECRET','service_pid':100},
            'pipe':{'kind':'WIN32','stage':'PIPE_READ_HEADER','win32':109,'message':'PRIVATE_SECRET','trace':[{'stage':'PIPE_WRITE','at_ms':1,'secret':'PRIVATE_SECRET'}]*100}}
        data=advanced_status(SimpleNamespace(desktop_bridge=bridge))
        self.assertFalse(data['local']['account_match']);self.assertEqual(len(data['pipe']['trace']),16)
        self.assertIsNone(data['local']['client_session']);self.assertNotIn('PRIVATE_SECRET',json.dumps(data))
        self.assertEqual(clean_probe({'stage':{},'kind':[]})['kind'],'UNKNOWN')

    def test_synchronous_read_counts_bytes(self):
        b=DesktopBridge.__new__(DesktopBridge);b.kernel=Mock();b.handle=1;b.deadline=time.monotonic()+1;b.read_bytes=0
        def read(handle,buffer,count,transferred,overlapped):
            C.memmove(buffer,b'abc',3);transferred._obj.value=3;return 1
        b.kernel.CreateEventW.return_value=2;b.kernel.ReadFile.side_effect=read
        self.assertEqual(b.io(count=3),b'abc');self.assertEqual(b.read_bytes,3)

    def test_native_read_error_number_not_lost(self):
        b=DesktopBridge.__new__(DesktopBridge);b.kernel=Mock();b.handle=1;b.deadline=time.monotonic()+1
        b.kernel.CreateEventW.return_value=2;b.kernel.ReadFile.return_value=0
        with patch('desktop_bridge.C.get_last_error',return_value=109,create=True):
            with self.assertRaises(BridgeFailure) as error:b.io(count=3)
        self.assertEqual(error.exception.errno,109)
