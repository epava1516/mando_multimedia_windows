"""Explicit bounded Unicode clipboard access on the normal user desktop only."""
import ctypes as C


def validate_text(value):
    if not isinstance(value, str) or len(value) > 1000 or '\x00' in value:
        raise ValueError('Texto del portapapeles fuera de rango')
    if len(value.encode('utf-16-le')) > 2000:
        raise ValueError('Texto del portapapeles fuera de rango')
    return value


class WindowsClipboard:
    def __init__(self, desktop_available):
        self.desktop_available = desktop_available
        self.user = C.WinDLL('user32', use_last_error=True)
        self.kernel = C.WinDLL('kernel32', use_last_error=True)
        signatures = [
            (self.user, 'OpenClipboard', [C.c_void_p], C.c_int),
            (self.user, 'CloseClipboard', [], C.c_int),
            (self.user, 'EmptyClipboard', [], C.c_int),
            (self.user, 'GetClipboardData', [C.c_uint], C.c_void_p),
            (self.user, 'SetClipboardData', [C.c_uint, C.c_void_p], C.c_void_p),
            (self.user, 'IsClipboardFormatAvailable', [C.c_uint], C.c_int),
            (self.user, 'CreateWindowExW', [C.c_uint,C.c_wchar_p,C.c_wchar_p,C.c_uint,C.c_int,C.c_int,C.c_int,C.c_int,C.c_void_p,C.c_void_p,C.c_void_p,C.c_void_p], C.c_void_p),
            (self.user, 'DestroyWindow', [C.c_void_p], C.c_int),
            (self.kernel, 'GlobalAlloc', [C.c_uint,C.c_size_t], C.c_void_p),
            (self.kernel, 'GlobalLock', [C.c_void_p], C.c_void_p),
            (self.kernel, 'GlobalUnlock', [C.c_void_p], C.c_int),
            (self.kernel, 'GlobalSize', [C.c_void_p], C.c_size_t),
            (self.kernel, 'GlobalFree', [C.c_void_p], C.c_void_p),
        ]
        for dll,name,args,result in signatures:
            function=getattr(dll,name);function.argtypes=args;function.restype=result

    def exchange(self, value=None):
        if value is not None: validate_text(value)
        if not self.desktop_available(): raise RuntimeError('Desbloquea Windows para compartir el portapapeles')
        # A real message-only owner is needed by EmptyClipboard/SetClipboardData.
        owner=self.user.CreateWindowExW(0,'STATIC','MandoClipboard',0,0,0,0,0,C.c_void_p(-3),None,None,None)
        if not owner: raise OSError('Portapapeles no disponible')
        opened=False;memory=None
        try:
            if not self.user.OpenClipboard(owner): raise OSError('Portapapeles ocupado')
            opened=True
            if not self.desktop_available(): raise RuntimeError('Escritorio bloqueado')
            if value is None:
                if not self.user.IsClipboardFormatAvailable(13): raise ValueError('El portapapeles no contiene texto')
                handle=self.user.GetClipboardData(13)
                size=self.kernel.GlobalSize(handle) if handle else 0
                if size < 2 or size > 8192: raise ValueError('Portapapeles fuera de rango')
                pointer=self.kernel.GlobalLock(handle)
                if not pointer: raise OSError('Portapapeles no disponible')
                try:
                    raw=C.string_at(pointer,size)
                    end=next((i for i in range(0,len(raw)-1,2) if raw[i:i+2]==b'\0\0'),None)
                    if end is None: raise ValueError('Texto sin terminador')
                    return validate_text(raw[:end].decode('utf-16-le'))
                finally: self.kernel.GlobalUnlock(handle)
            raw=value.encode('utf-16-le')+b'\0\0'
            memory=self.kernel.GlobalAlloc(2,len(raw))
            if not memory: raise OSError('Sin memoria de portapapeles')
            pointer=self.kernel.GlobalLock(memory)
            if not pointer: raise OSError('Portapapeles no disponible')
            try: C.memmove(pointer,raw,len(raw))
            finally: self.kernel.GlobalUnlock(memory)
            if not self.user.EmptyClipboard() or not self.user.SetClipboardData(13,memory):
                raise OSError('Windows no ha aceptado el portapapeles')
            memory=None  # Ownership transferred to Windows.
            return None
        finally:
            if memory: self.kernel.GlobalFree(memory)
            if opened: self.user.CloseClipboard()
            self.user.DestroyWindow(owner)
