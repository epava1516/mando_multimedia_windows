import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import threading
import time
import unittest
from unittest.mock import Mock,patch
from desktop_bridge import DesktopBridge

class ProbeTests(unittest.TestCase):
    def test_expired_probe_never_touches_pipe(self):
        bridge=DesktopBridge.__new__(DesktopBridge)
        bridge.deadline=time.monotonic()-1;bridge.kernel=Mock()
        with self.assertRaises(TimeoutError):bridge.io(count=4)
        bridge.kernel.CreateEventW.assert_not_called()

    def test_status_uses_short_total_deadline_and_keeps_identity_check(self):
        bridge=DesktopBridge.__new__(DesktopBridge)
        bridge.lock=threading.Lock();bridge.connect=Mock(side_effect=OSError('not ready'));bridge.close=Mock()
        start=time.monotonic()
        with self.assertRaises(OSError):bridge.request({'op':'status'})
        self.assertGreater(bridge.deadline,start)
        self.assertLess(bridge.deadline-start,.5)
        bridge.connect.assert_called_once();bridge.close.assert_called_once()
