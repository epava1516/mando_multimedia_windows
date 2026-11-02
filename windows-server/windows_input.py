"""Windows input adapter. No shells, remote commands, or app automation."""
import ctypes as C
import sys
import os
from pathlib import Path
import subprocess

KEYS = {
    'CTRL': 0x11, 'ALT': 0x12, 'SHIFT': 0x10, 'WIN': 0x5B,
    'ESC': 0x1B, 'TAB': 0x09, 'ENTER': 0x0D, 'SPACE': 0x20,
    'BACKSPACE': 0x08, 'DELETE': 0x2E, 'INSERT': 0x2D,
    'HOME': 0x24, 'END': 0x23, 'PAGEUP': 0x21, 'PAGEDOWN': 0x22,
    'LEFT': 0x25, 'UP': 0x26, 'RIGHT': 0x27, 'DOWN': 0x28,
    'PLAY_PAUSE': 0xB3, 'STOP': 0xB2, 'PREVIOUS': 0xB1, 'NEXT': 0xB0,
    'MUTE': 0xAD, 'VOLUME_DOWN': 0xAE, 'VOLUME_UP': 0xAF,
    **{f'F{i}': 0x6F+i for i in range(1, 13)},
    **{chr(i): i for i in range(0x30, 0x5B) if chr(i).isalnum()},
}
MODIFIERS = {'CTRL', 'ALT', 'SHIFT', 'WIN'}
EXTENDED = {0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28, 0x2D, 0x2E,
            0x5B, 0xAD, 0xAE, 0xAF, 0xB0, 0xB1, 0xB2, 0xB3}

# Fixed-width Windows ABI types, including when imported by tests on Linux.
class Mouse(C.Structure):
    _fields_ = [('dx', C.c_int32), ('dy', C.c_int32), ('data', C.c_uint32),
                ('flags', C.c_uint32), ('time', C.c_uint32), ('extra', C.c_size_t)]

class Keyboard(C.Structure):
    _fields_ = [('vk', C.c_uint16), ('scan', C.c_uint16), ('flags', C.c_uint32),
                ('time', C.c_uint32), ('extra', C.c_size_t)]

class Hardware(C.Structure):
    _fields_ = [('msg', C.c_uint32), ('low', C.c_uint16), ('high', C.c_uint16)]

class Payload(C.Union):
    _fields_ = [('mi', Mouse), ('ki', Keyboard), ('hi', Hardware)]

class Input(C.Structure):
    _fields_ = [('kind', C.c_uint32), ('payload', Payload)]

def key_event(vk, up=False, scan=0, unicode=False):
    flags = (2 if up else 0) | (4 if unicode else (1 if vk in EXTENDED else 0))
    return Input(1, Payload(ki=Keyboard(vk, scan, flags, 0, 0)))

def mouse_event(flags, dx=0, dy=0, data=0):
    return Input(0, Payload(mi=Mouse(dx, dy, data & 0xFFFFFFFF, flags, 0, 0)))

class WindowsInput:
    def __init__(self):
        if sys.platform != 'win32':
            raise RuntimeError('El control real requiere Windows. Usa --dry-run para probar la interfaz.')
        self.api = C.WinDLL('user32', use_last_error=True)
        self.api.SendInput.argtypes = [C.c_uint32, C.POINTER(Input), C.c_int]
        self.api.SendInput.restype = C.c_uint32
        from desktop_bridge import DesktopBridge
        self.desktop_bridge = DesktopBridge()

    def send(self, events):
        array = (Input * len(events))(*events)
        if self.api.SendInput(len(events), array, C.sizeof(Input)) != len(events):
            # Release any modifiers/buttons if Windows only accepted part of a batch.
            release = [key_event(KEYS[k], True) for k in MODIFIERS]
            release.append(mouse_event(0x14))
            self.api.SendInput(len(release), (Input * len(release))(*release), C.sizeof(Input))
            raise RuntimeError('Windows rechazó la entrada. Comprueba la ventana activa y sus permisos.')

    def default_desktop(self):
        self.api.OpenInputDesktop.argtypes = [C.c_uint32, C.c_int, C.c_uint32]
        self.api.OpenInputDesktop.restype = C.c_void_p
        self.api.GetUserObjectInformationW.argtypes = [C.c_void_p,C.c_int,C.c_void_p,C.c_uint32,C.POINTER(C.c_uint32)]
        self.api.GetUserObjectInformationW.restype = C.c_int
        self.api.CloseDesktop.argtypes = [C.c_void_p]
        self.api.CloseDesktop.restype = C.c_int
        desktop = self.api.OpenInputDesktop(0,False,1)
        if not desktop:
            return False
        try:
            name = C.create_unicode_buffer(128); needed = C.c_uint32()
            return bool(self.api.GetUserObjectInformationW(desktop,2,name,C.sizeof(name),C.byref(needed))) and name.value.lower() == 'default'
        finally:
            self.api.CloseDesktop(desktop)

    def secure_desktop_available(self):
        return self.desktop_bridge.available()

    def zoom_path(self):
        integrated = Path(os.environ['ProgramFiles']) / 'MandoDesktop' / 'MandoTouchZoom.exe'
        if integrated.is_file(): return integrated
        return Path(os.environ['LOCALAPPDATA']) / 'MandoTouchZoom' / 'MandoTouchZoom.exe'

    def zoom_available(self):
        return self.zoom_path().is_file()

    def execute(self, action):
        kind = action['type']
        if kind != 'lock' and not self.default_desktop():
            if kind not in ('move','click','scroll','key','text'):
                raise RuntimeError('Ese gesto requiere el escritorio normal')
            self.desktop_bridge.execute(action)
            return
        if kind == 'lock':
            self.api.LockWorkStation.argtypes = []
            self.api.LockWorkStation.restype = C.c_int
            if not self.api.LockWorkStation():
                raise RuntimeError('Windows rechazo el bloqueo')
        elif kind == 'task_view':
            self.execute({'type':'key','key':'TAB','modifiers':['WIN']})
        elif kind == 'zoom':
            if not self.zoom_available():
                raise RuntimeError('Instala MandoTouchZoom')
            if action['steps']:
                result = subprocess.run([str(self.zoom_path()), 'in' if action['steps'] > 0 else 'out'],
                                        shell=False, timeout=2, creationflags=0x08000000, check=False)
                if result.returncode:
                    raise RuntimeError('El gesto tactil no se ha aceptado')
        elif kind == 'key':
            codes = [KEYS[k] for k in action['modifiers']] + [KEYS[action['key']]]
            self.send([key_event(k) for k in codes] + [key_event(k, True) for k in reversed(codes)])
        elif kind == 'text':
            units = action['text'].encode('utf-16-le')
            events = []
            for i in range(0, len(units), 2):
                scan = int.from_bytes(units[i:i+2], 'little')
                events.extend([key_event(0, scan=scan, unicode=True),
                               key_event(0, True, scan, True)])
            if events:
                self.send(events)
        elif kind == 'move':
            self.send([mouse_event(1, action['dx'], action['dy'])])
        elif kind == 'click':
            down, up = (2, 4) if action['button'] == 'left' else (8, 16)
            self.send([mouse_event(down), mouse_event(up)])
        elif kind == 'scroll':
            self.send([mouse_event(0x800, data=action['steps'] * 120)])

class DryRunInput:
    """Validate and acknowledge commands without issuing host input."""
    def zoom_available(self):
        return True

    def secure_desktop_available(self):
        return False

    def execute(self, action):
        pass
