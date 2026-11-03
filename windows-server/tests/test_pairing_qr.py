import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import unittest
from pairing_qr import frame

class QrTests(unittest.TestCase):
    def test_frame_is_bounded_square_with_quiet_zone(self):
        code='mando1:192.168.1.10:1979:'+('a'*64)+':12345678'
        kind,width,bits=frame(code).split(' ');width=int(width)
        self.assertEqual(kind,'MANDO_QR');self.assertEqual(len(bits),width*width)
        self.assertLessEqual(width,97);self.assertEqual(bits[:width*4],'0'*(width*4))
        self.assertEqual(bits[-width*4:],'0'*(width*4))
        self.assertNotIn(code,frame(code))
        for invalid in ['https://example.invalid','',code+'\n']:
            with self.assertRaises(ValueError):frame(invalid)
