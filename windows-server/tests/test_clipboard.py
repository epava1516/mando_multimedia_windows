import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import unittest
from unittest.mock import Mock, patch
import server
from windows_clipboard import validate_text, WindowsClipboard

class ClipboardTests(unittest.TestCase):
    def test_text_limits(self):
        for text in ['', 'hola\nPC', 'á😀', 'x'*1000]:
            self.assertEqual(validate_text(text),text)
        for text in [None, 12, 'a\0b', 'x'*1001, '😀'*501, '\ud800']:
            with self.assertRaises((ValueError,UnicodeError)): validate_text(text)

    def test_secure_desktop_never_opens_clipboard(self):
        clip=WindowsClipboard.__new__(WindowsClipboard)
        clip.desktop_available=lambda:False
        clip.user=Mock();clip.kernel=Mock()
        with self.assertRaises(RuntimeError):clip.exchange()
        clip.user.OpenClipboard.assert_not_called()
        clip.user.CreateWindowExW.assert_not_called()

    def test_write_transfers_ownership_and_closes(self):
        clip=WindowsClipboard.__new__(WindowsClipboard)
        clip.desktop_available=lambda:True
        clip.user=Mock();clip.kernel=Mock()
        with patch('windows_clipboard.C.memmove'):
            clip.exchange('hola')
        clip.user.SetClipboardData.assert_called_once_with(13,clip.kernel.GlobalAlloc.return_value)
        clip.kernel.GlobalFree.assert_not_called()
        clip.user.CloseClipboard.assert_called_once()
        clip.user.DestroyWindow.assert_called_once()

    def test_failed_write_frees_memory_and_closes(self):
        clip=WindowsClipboard.__new__(WindowsClipboard)
        clip.desktop_available=lambda:True
        clip.user=Mock();clip.kernel=Mock();clip.user.SetClipboardData.return_value=0
        with patch('windows_clipboard.C.memmove'):
            with self.assertRaises(OSError):clip.exchange('hola')
        clip.kernel.GlobalFree.assert_called_once()
        clip.user.CloseClipboard.assert_called_once()

    def test_reads_unicode_within_global_size(self):
        clip=WindowsClipboard.__new__(WindowsClipboard)
        clip.desktop_available=lambda:True
        clip.user=Mock();clip.kernel=Mock();clip.kernel.GlobalSize.return_value=12
        with patch('windows_clipboard.C.string_at',return_value='hola'.encode('utf-16-le')+b'\0\0\0\0'):
            self.assertEqual(clip.exchange(),'hola')
        clip.kernel.GlobalUnlock.assert_called_once()
        clip.user.CloseClipboard.assert_called_once()

    def test_invalid_request_never_reaches_native_adapter(self):
        adapter=Mock();state=server.State(adapter)
        for request in [{}, {'direction':'write','text':'x'*1001}, {'direction':'other'}]:
            with self.assertRaises(ValueError):state.clipboard(request)
        adapter.default_desktop.assert_not_called()

    def test_dry_run_never_reads_host_clipboard(self):
        from windows_input import DryRunInput
        state=server.State(DryRunInput())
        self.assertFalse(state.capabilities()['clipboard_text'])
        with self.assertRaises(ValueError):state.clipboard({'direction':'read'})
