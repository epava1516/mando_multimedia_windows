"""Restricted local broker client. Verifies the named-pipe PID against the Windows service."""
import ctypes as C
from ctypes import wintypes as W
import json
import struct
import threading
import time

PIPE = r'\\.\pipe\Mando.Desktop.v1'
SERVICE = 'MandoDesktopBroker'


class DesktopBridge:
    def __init__(self):
        self.lock=threading.Lock()
        self.last_error="DESKTOP_NOT_CHECKED"
        self.stage="PIPE_OPEN"
        self.handle=None
        self.kernel=C.WinDLL('kernel32',use_last_error=True)
        self.security=C.WinDLL('advapi32',use_last_error=True)
        self.kernel.CreateFileW.argtypes=[W.LPCWSTR,W.DWORD,W.DWORD,C.c_void_p,W.DWORD,W.DWORD,W.HANDLE]
        self.kernel.CreateFileW.restype=W.HANDLE
        self.kernel.CloseHandle.argtypes=[W.HANDLE]
        self.kernel.GetNamedPipeServerProcessId.argtypes=[W.HANDLE,C.POINTER(W.ULONG)]
        self.security.OpenSCManagerW.argtypes=[W.LPCWSTR,W.LPCWSTR,W.DWORD]
        self.security.OpenSCManagerW.restype=W.HANDLE
        self.security.OpenServiceW.argtypes=[W.HANDLE,W.LPCWSTR,W.DWORD]
        self.security.OpenServiceW.restype=W.HANDLE
        self.security.QueryServiceStatusEx.argtypes=[W.HANDLE,C.c_int,C.c_void_p,W.DWORD,C.POINTER(W.DWORD)]
        self.security.CloseServiceHandle.argtypes=[W.HANDLE]
        self.kernel.CreateEventW.argtypes=[C.c_void_p,W.BOOL,W.BOOL,W.LPCWSTR]
        self.kernel.CreateEventW.restype=W.HANDLE

    def close(self):
        previous=self.handle;self.handle=None
        if previous is not None:self.kernel.CloseHandle(previous)

    def connect(self):
        if self.handle is not None:return
        self.stage="PIPE_OPEN"
        handle=self.kernel.CreateFileW(PIPE,0x0012019B,0,None,3,0x40000000,None)
        if handle==C.c_void_p(-1).value:raise OSError(C.get_last_error(),'No se puede abrir el componente local')
        manager=service=None
        try:
            self.stage="SERVICE_IDENTITY"
            peer=W.ULONG()
            if not self.kernel.GetNamedPipeServerProcessId(handle,C.byref(peer)):raise OSError('Identidad IPC no disponible')
            manager=self.security.OpenSCManagerW(None,None,1)
            if not manager:raise OSError('SCM no disponible')
            service=self.security.OpenServiceW(manager,SERVICE,4)
            if not service:raise OSError('Servicio no instalado')
            status=(W.DWORD*9)();needed=W.DWORD()
            if not self.security.QueryServiceStatusEx(service,0,status,C.sizeof(status),C.byref(needed)):raise OSError('Estado de servicio no disponible')
            # SERVICE_RUNNING=4, PROCESS_ID at field 7. Avoid a same-name pipe planted by a user.
            if status[1]!=4 or status[7]!=peer.value:raise OSError('El pipe no pertenece al servicio de Mando')
            self.handle=handle
        except BaseException:
            self.kernel.CloseHandle(handle);raise
        finally:
            if service:self.security.CloseServiceHandle(service)
            if manager:self.security.CloseServiceHandle(manager)

    def io(self,data=None,count=0):
        if time.monotonic()>=self.deadline:raise TimeoutError('El componente de escritorio no responde')
        class OVERLAPPED(C.Structure):
            _fields_=[('internal',C.c_size_t),('internal_high',C.c_size_t),('offset',W.DWORD),('offset_high',W.DWORD),('event',W.HANDLE)]
        buffer=C.create_string_buffer(data if data is not None else count)
        event=self.kernel.CreateEventW(None,True,False,None)
        if not event:raise OSError('No se puede crear evento IPC')
        pending=OVERLAPPED();pending.event=event;transferred=W.DWORD()
        function=self.kernel.WriteFile if data is not None else self.kernel.ReadFile
        function.argtypes=[W.HANDLE,C.c_void_p,W.DWORD,C.POINTER(W.DWORD),C.POINTER(OVERLAPPED)]
        function.restype=W.BOOL
        self.kernel.WaitForSingleObject.argtypes=[W.HANDLE,W.DWORD]
        self.kernel.GetOverlappedResult.argtypes=[W.HANDLE,C.POINTER(OVERLAPPED),C.POINTER(W.DWORD),W.BOOL]
        self.kernel.CancelIoEx.argtypes=[W.HANDLE,C.POINTER(OVERLAPPED)]
        try:
            ok=function(self.handle,buffer,len(data) if data is not None else count,C.byref(transferred),C.byref(pending))
            if not ok:
                if C.get_last_error()!=997:raise OSError('Canal IPC cerrado')
                if self.kernel.WaitForSingleObject(event,max(0,int((self.deadline-time.monotonic())*1000)))!=0:
                    self.kernel.CancelIoEx(self.handle,C.byref(pending))
                    # Keep the OVERLAPPED and buffer alive until cancellation has completed.
                    self.kernel.GetOverlappedResult(self.handle,C.byref(pending),C.byref(transferred),True)
                    raise TimeoutError('El componente de escritorio no responde')
                if not self.kernel.GetOverlappedResult(self.handle,C.byref(pending),C.byref(transferred),False):raise OSError('Entrada IPC rechazada')
            if not transferred.value:raise OSError('Canal IPC cerrado')
            return transferred.value if data is not None else buffer.raw[:transferred.value]
        finally:self.kernel.CloseHandle(event)

    def read(self,count):
        result=bytearray()
        while len(result)<count:result.extend(self.io(count=count-len(result)))
        return bytes(result)

    def request(self,message):
        with self.lock:
            self.deadline=time.monotonic()+(.4 if message.get('op')=='status' else 3)
            try:
                self.connect()
                self.stage="PIPE_STATUS" if message.get("op")=="status" else "PIPE_INPUT"
                body=json.dumps(message,ensure_ascii=True,separators=(',',':')).encode('utf-8')
                if len(body)>8192:raise ValueError('Mensaje IPC excesivo')
                packet=struct.pack('!I',len(body))+body;offset=0
                while offset<len(packet):offset+=self.io(data=packet[offset:])
                count,=struct.unpack('!I',self.read(4))
                if not 1<=count<=8192:raise ValueError('Respuesta IPC invalida')
                response=json.loads(self.read(count))
                if not isinstance(response,dict) or response.get('ok') is not True:raise OSError('Escritorio rechazado por el servicio')
                return response
            except BaseException:self.close();raise

    def available(self):
        # A status probe may reconnect after idle shutdown. Input is never retried.
        for _ in range(2):
            try:
                result=self.request({'op':'status'}).get('available') is True
                self.last_error='DESKTOP_OK' if result else 'DESKTOP_REJECTED'
                return result
            except (OSError,ValueError) as error:
                number=error.errno if isinstance(error,OSError) and type(error.errno) is int else C.get_last_error()
                self.last_error='DESKTOP_'+self.stage+'_'+str(number or 0)
        return False

    def execute(self,action):
        self.request({'op':'input','action':action})
