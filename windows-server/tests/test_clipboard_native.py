"""Opt-in isolated Windows runner test. Replaces only the runner clipboard with fixtures."""
import ctypes as C
import os
from pathlib import Path
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from windows_clipboard import WindowsClipboard


@unittest.skipUnless(sys.platform=='win32' and os.environ.get('MANDO_TEST_CLIPBOARD')=='1',
                     'Explicit isolated Windows clipboard fixture only')
class NativeClipboardTests(unittest.TestCase):
    def setUp(self):
        self.clip=WindowsClipboard(lambda:True)

    def tearDown(self):
        self.clip.exchange('')

    def test_url_with_dataobject_before_unicode_text(self):
        url='https://example.invalid/ruta?q=prueba&otro=%C3%B1#seccion'
        user,kernel=self.clip.user,self.clip.kernel
        user.RegisterClipboardFormatW.argtypes=[C.c_wchar_p]
        user.RegisterClipboardFormatW.restype=C.c_uint
        custom=user.RegisterClipboardFormatW('DataObject')
        self.assertGreater(custom,0)
        owner=user.CreateWindowExW(0,'STATIC','MandoTestClipboard',0,0,0,0,0,C.c_void_p(-3),None,None,None)
        self.assertTrue(owner)
        opened=False
        try:
            self.assertTrue(user.OpenClipboard(owner));opened=True
            self.assertTrue(user.EmptyClipboard())
            # Reproduce the official reader's troublesome format before actual text.
            for format_id,raw in [(custom,b'Synthetic browser metadata\0'),(13,url.encode('utf-16-le')+b'\0\0')]:
                memory=kernel.GlobalAlloc(2,len(raw));self.assertTrue(memory)
                try:
                    pointer=kernel.GlobalLock(memory);self.assertTrue(pointer)
                    try:C.memmove(pointer,raw,len(raw))
                    finally:kernel.GlobalUnlock(memory)
                    self.assertTrue(user.SetClipboardData(format_id,memory))
                    memory=None
                finally:
                    if memory:kernel.GlobalFree(memory)
        finally:
            if opened:user.CloseClipboard()
            user.DestroyWindow(owner)
        self.assertEqual(self.clip.exchange(),url)

    def test_unicode_url_roundtrip(self):
        url='https://example.invalid/búsqueda?q=ñ&emoji=🙂#fin'
        self.clip.exchange(url)
        self.assertEqual(self.clip.exchange(),url)


if __name__=='__main__':unittest.main()
