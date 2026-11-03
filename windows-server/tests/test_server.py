import hashlib
import json
from pathlib import Path
import socket
import ssl
import struct
import sys
import tempfile
import threading
import time
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import server
from windows_input import WindowsInput, DryRunInput, Input, key_event, mouse_event
from unittest.mock import Mock, patch
from types import SimpleNamespace


class Recorder(DryRunInput):
    def __init__(self): self.executed=[]
    def execute(self, action): self.executed.append(action)


class ProtocolTests(unittest.TestCase):
    def test_rejects_invalid_actions_before_adapter(self):
        state=server.State(Recorder())
        invalid=[{}, {'type':'shell','command':'whoami'}, {'type':'move','dx':True,'dy':0},
                 {'type':'move','dx':501,'dy':0}, {'type':'scroll','steps':1.5},
                 {'type':'text','text':'\ud800'}, {'type':'text','text':'x'*501}, {'type':'text','text':'\x00'},
                 {'type':'key','key':'CTRL'}, {'type':'key','key':'A','modifiers':['CTRL','CTRL']},
                 {'type':'key','key':'A','modifiers':['INVALID']}, {'type':'zoom','steps':100}]
        for action in invalid:
            with self.subTest(action_type=action.get('type')):
                with self.assertRaises((ValueError,UnicodeError)): state.execute(action)
        self.assertEqual(state.adapter.executed,[])

    def test_pin_expiration_rotation_and_limits(self):
        now=[1000.0];state=server.State(Recorder(),lambda:now[0])
        old=state.pin;self.assertTrue(state.pair(old))
        now[0]+=601;self.assertFalse(state.pair(old))
        state.rotate_pin();self.assertEqual(state.clients,1)
        self.assertTrue(state.pair(state.pin));self.assertTrue(state.pair(state.pin));self.assertTrue(state.pair(state.pin))
        self.assertFalse(state.pair(state.pin));state.unpair();self.assertTrue(state.pair(state.pin))

    def test_global_attempt_throttle(self):
        state=server.State(Recorder())
        for _ in range(10):self.assertFalse(state.pair('wrong'))
        self.assertFalse(state.pair(state.pin))

    def test_global_action_throttle(self):
        state=server.State(Recorder())
        for _ in range(120):state.execute({'type':'task_view'})
        with self.assertRaises(ValueError):state.execute({'type':'task_view'})
        self.assertEqual(len(state.adapter.executed),120)

    def test_oversized_and_nonobject_frames(self):
        for raw in [struct.pack('!I',8193),struct.pack('!I',2)+b'[]',struct.pack('!I',3)+b'NaN']:
            a,b=socket.socketpair()
            try:
                a.sendall(raw)
                with self.assertRaises(ValueError):server.receive(b)
            finally:a.close();b.close()

    def test_secure_desktop_input_is_forwarded_to_restricted_broker(self):
        adapter=WindowsInput.__new__(WindowsInput)
        adapter.default_desktop=lambda:False
        adapter.send=Mock();adapter.desktop_bridge=Mock()
        action={'type':'move','dx':1,'dy':1}
        adapter.execute(action)
        adapter.desktop_bridge.execute.assert_called_once_with(action)
        adapter.send.assert_not_called()
        with self.assertRaises(RuntimeError):adapter.execute({'type':'task_view'})

    def test_lock_uses_api_not_keyboard_shortcut(self):
        adapter=WindowsInput.__new__(WindowsInput)
        adapter.default_desktop=lambda:False
        lock=Mock(return_value=1)
        adapter.api=SimpleNamespace(LockWorkStation=lock)
        adapter.send=Mock()
        adapter.execute({'type':'lock'})
        lock.assert_called_once_with();adapter.send.assert_not_called()

    def test_zoom_uses_fixed_program_and_argument(self):
        adapter=WindowsInput.__new__(WindowsInput)
        adapter.default_desktop=lambda:True
        adapter.zoom_available=lambda:True
        helper=Path('/fixed/MandoTouchZoom.exe');adapter.zoom_path=lambda:helper
        with patch('windows_input.subprocess.run',return_value=SimpleNamespace(returncode=0)) as run:
            adapter.execute({'type':'zoom','steps':-1})
            run.assert_called_once_with([str(helper),'out'],shell=False,timeout=2,creationflags=0x08000000,check=False)

    def test_device_authorization_persists_as_hash_and_revokes(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'devices.json'
            state=server.State(Recorder(),device_file=path)
            device=state.issue_device()
            self.assertNotIn(device['token'],path.read_text())
            restarted=server.State(Recorder(),device_file=path)
            self.assertTrue(restarted.resume(device['id'],device['token']))
            self.assertFalse(restarted.resume(device['id'],'a'*43))
            old_pin=restarted.pin
            restarted.revoke_devices()
            self.assertFalse(restarted.pair(old_pin))
            again=server.State(Recorder(),device_file=path)
            self.assertFalse(again.resume(device['id'],device['token']))

    def test_device_limit_is_enforced(self):
        state=server.State(Recorder())
        for _ in range(4):state.issue_device()
        with self.assertRaises(ValueError):state.issue_device()
        self.assertFalse(state.pair(state.pin))

    def test_wheel_axes_and_directions(self):
        adapter=WindowsInput.__new__(WindowsInput);adapter.default_desktop=lambda:True;adapter.send=Mock()
        adapter.execute({'type':'scroll','steps':2})
        vertical=adapter.send.call_args.args[0]
        self.assertEqual(len(vertical),1);self.assertEqual(vertical[0].payload.mi.flags,0x800)
        self.assertEqual(vertical[0].payload.mi.data,(-240)&0xffffffff)
        adapter.execute({'type':'scroll_horizontal','steps':2})
        horizontal=adapter.send.call_args.args[0]
        self.assertEqual(len(horizontal),1);self.assertEqual(horizontal[0].payload.mi.flags,0x1000)
        self.assertEqual(horizontal[0].payload.mi.data,240)
        with self.assertRaises(ValueError):server.validate_action({'type':'scroll_horizontal','steps':True})

    def test_fixed_windows_input_abi(self):
        import ctypes
        self.assertEqual(ctypes.sizeof(Input),40 if ctypes.sizeof(ctypes.c_void_p)==8 else 28)
        self.assertEqual(key_event(0x5B,True).payload.ki.flags,3)
        self.assertEqual(mouse_event(0x800,data=-120).payload.mi.data,0xffffff88)


class TLSTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory=tempfile.TemporaryDirectory()
        cls.context,cls.fingerprint=server.identity(Path(cls.directory.name))

    @classmethod
    def tearDownClass(cls):cls.directory.cleanup()

    def setUp(self):
        self.adapter=Recorder();self.state=server.State(self.adapter)
        self.server=server.Server(('127.0.0.1',0),self.state,self.context)
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start()
        self.connections=[]

    def tearDown(self):
        for connection in self.connections:connection.close()
        self.server.shutdown();self.server.server_close();self.thread.join()

    def connect(self):
        context=ssl.create_default_context(cafile=str(Path(self.directory.name)/'server-cert.pem'))
        context.check_hostname=False # Exact generated trust anchor, not an arbitrary self-signed peer.
        connection=context.wrap_socket(socket.create_connection(self.server.server_address,timeout=2),server_hostname='mando')
        connection.settimeout(2);self.connections.append(connection)
        self.assertEqual(hashlib.sha256(connection.getpeercert(binary_form=True)).hexdigest(),self.fingerprint)
        self.assertEqual(server.receive(connection),{'server':'mando','protocol':1})
        return connection

    def pair(self):
        connection=self.connect();server.send(connection,{'op':'pair','protocol':1,'pin':self.state.pin})
        reply=server.receive(connection);self.last_device=reply['device'];self.assertTrue(reply['ok']);self.assertFalse(reply['capabilities']['secure_desktop'])
        return connection

    def test_tls_pair_actions_and_status(self):
        connection=self.pair()
        actions=[{'type':'move','dx':12,'dy':-7},{'type':'click','button':'left'},
                 {'type':'text','text':'hola ñ 🙂'},{'type':'key','key':'VOLUME_UP'},
                 {'type':'scroll','steps':-2},{'type':'task_view'},{'type':'lock'},{'type':'zoom','steps':1}]
        for number,action in enumerate(actions,1):
            server.send(connection,{'op':'action','id':number,'action':action})
            self.assertEqual(server.receive(connection),{'ok':True,'id':number})
        self.assertEqual(self.adapter.executed,[server.validate_action(action) for action in actions])
        server.send(connection,{'op':'status','id':9});self.assertTrue(server.receive(connection)['capabilities']['dry_run'])

    def test_resume_works_after_pin_expired(self):
        first=self.pair();device=self.last_device;first.close()
        self.state.pin_until=-1
        connection=self.connect()
        server.send(connection,{'op':'resume','protocol':1,'device':device['id'],'token':device['token']})
        self.assertTrue(server.receive(connection)['ok'])
        server.send(connection,{'op':'action','id':1,'action':{'type':'key','key':'ENTER'}})
        self.assertTrue(server.receive(connection)['ok'])
        self.assertEqual(len(self.adapter.executed),1)

    def test_revocation_blocks_live_session(self):
        connection=self.pair();self.state.revoke_devices()
        server.send(connection,{'op':'action','id':1,'action':{'type':'click','button':'left'}})
        with self.assertRaises((EOFError,OSError)):server.receive(connection)
        self.assertEqual(self.adapter.executed,[])

    def test_no_input_before_pairing(self):
        connection=self.connect();server.send(connection,{'op':'action','id':1,'action':{'type':'lock'}})
        self.assertIn('error',server.receive(connection));self.assertEqual(self.adapter.executed,[])

    def test_wrong_pin_rejected(self):
        connection=self.connect();server.send(connection,{'op':'pair','protocol':1,'pin':'incorrect'})
        self.assertIn('error',server.receive(connection));self.assertEqual(self.state.clients,0)

    def test_replayed_sequence_not_executed(self):
        connection=self.pair();message={'op':'action','id':1,'action':{'type':'click','button':'left'}}
        server.send(connection,message);self.assertTrue(server.receive(connection)['ok'])
        server.send(connection,message)
        with self.assertRaises((EOFError,OSError)):server.receive(connection)
        self.assertEqual(len(self.adapter.executed),1)

    def test_bad_action_not_executed(self):
        connection=self.pair();server.send(connection,{'op':'action','id':1,'action':{'type':'execute','path':'cmd.exe'}})
        self.assertIn('error',server.receive(connection));self.assertEqual(self.adapter.executed,[])

    def test_untrusted_certificate_rejected(self):
        context=ssl.create_default_context()
        raw=socket.create_connection(self.server.server_address,timeout=2)
        try:
            with self.assertRaises(ssl.SSLCertVerificationError):context.wrap_socket(raw,server_hostname='mando')
        finally:raw.close()

    def test_plaintext_cannot_pair(self):
        connection=socket.create_connection(self.server.server_address,timeout=2)
        try:
            try:
                connection.sendall(b'{"op":"pair"}\n');connection.shutdown(socket.SHUT_WR)
                self.assertEqual(connection.recv(100),b'')
            except OSError:pass  # TLS may reject and close before send/shutdown completes.
            self.assertEqual(self.state.clients,0)
        finally:connection.close()


if __name__=='__main__':unittest.main()
