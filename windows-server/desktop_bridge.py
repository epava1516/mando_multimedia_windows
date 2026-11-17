"""Restricted local broker client with bounded, value-free IPC diagnostics."""
import ctypes as C
from ctypes import wintypes as W
import json
import struct
import threading
import time

PIPE = r'\\.\pipe\Mando.Desktop.v1'
SERVICE = 'MandoDesktopBroker'
REASONS = frozenset({'BROKER_REJECTED','NO_CONSOLE','CLIENT_SESSION_QUERY','CLIENT_SESSION_MISMATCH','CLIENT_IDENTITY','CLIENT_ACCOUNT_MISMATCH'})

class BridgeFailure(OSError):
    def __init__(self, kind, number=0):
        super().__init__(number,kind)
        self.kind=kind

class DesktopBridge:
    def __init__(self):
        self.lock=threading.RLock()
        self.last_error='DESKTOP_NOT_CHECKED'
        self.stage='PIPE_OPEN'
        self.handle=None
        self.last_probe={}
        self.started=time.monotonic();self.trace=[]
        self.peer_pid=None;self.pid_matches=None;self.written=0;self.read_bytes=0
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

    def mark(self, stage):
        self.stage=stage
        if len(self.trace)<16:
            self.trace.append({'stage':stage,'at_ms':max(0,int((time.monotonic()-self.started)*1000))})

    def native_failure(self):
        return BridgeFailure('WIN32',max(0,C.get_last_error()))

    def close(self):
        previous=self.handle;self.handle=None
        if previous is not None:self.kernel.CloseHandle(previous)

    def connect(self):
        if self.handle is not None:return
        self.mark('PIPE_OPEN')
        handle=self.kernel.CreateFileW(PIPE,0x0012019B,0,None,3,0x40000000,None)
        if handle==C.c_void_p(-1).value:raise self.native_failure()
        manager=service=None
        try:
            self.mark('PIPE_PEER_PID')
            peer=W.ULONG()
            if not self.kernel.GetNamedPipeServerProcessId(handle,C.byref(peer)):raise self.native_failure()
            self.peer_pid=peer.value
            self.mark('SCM_OPEN')
            manager=self.security.OpenSCManagerW(None,None,1)
            if not manager:raise self.native_failure()
            self.mark('SERVICE_OPEN')
            service=self.security.OpenServiceW(manager,SERVICE,4)
            if not service:raise self.native_failure()
            self.mark('SERVICE_STATUS')
            status=(W.DWORD*9)();needed=W.DWORD()
            if not self.security.QueryServiceStatusEx(service,0,status,C.sizeof(status),C.byref(needed)):raise self.native_failure()
            self.mark('SERVICE_IDENTITY')
            self.pid_matches=status[1]==4 and status[7]==peer.value
            if not self.pid_matches:raise BridgeFailure('IDENTITY_MISMATCH')
            self.handle=handle
        except BaseException:
            self.kernel.CloseHandle(handle);raise
        finally:
            if service:self.security.CloseServiceHandle(service)
            if manager:self.security.CloseServiceHandle(manager)

    def io(self,data=None,count=0):
        if time.monotonic()>=self.deadline:raise TimeoutError('IPC deadline')
        class OVERLAPPED(C.Structure):
            _fields_=[('internal',C.c_size_t),('internal_high',C.c_size_t),('offset',W.DWORD),('offset_high',W.DWORD),('event',W.HANDLE)]
        buffer=C.create_string_buffer(data if data is not None else count)
        event=self.kernel.CreateEventW(None,True,False,None)
        if not event:raise self.native_failure()
        pending=OVERLAPPED();pending.event=event;transferred=W.DWORD()
        function=self.kernel.WriteFile if data is not None else self.kernel.ReadFile
        function.argtypes=[W.HANDLE,C.c_void_p,W.DWORD,C.POINTER(W.DWORD),C.POINTER(OVERLAPPED)]
        function.restype=W.BOOL
        self.kernel.WaitForSingleObject.argtypes=[W.HANDLE,W.DWORD]
        self.kernel.WaitForSingleObject.restype=W.DWORD
        self.kernel.GetOverlappedResult.argtypes=[W.HANDLE,C.POINTER(OVERLAPPED),C.POINTER(W.DWORD),W.BOOL]
        self.kernel.CancelIoEx.argtypes=[W.HANDLE,C.POINTER(OVERLAPPED)]
        try:
            ok=function(self.handle,buffer,len(data) if data is not None else count,C.byref(transferred),C.byref(pending))
            if not ok:
                if C.get_last_error()!=997:raise self.native_failure()
                waited=self.kernel.WaitForSingleObject(event,max(0,int((self.deadline-time.monotonic())*1000)))
                if waited!=0:
                    number=C.get_last_error() if waited==0xffffffff else 0
                    self.kernel.CancelIoEx(self.handle,C.byref(pending))
                    # Keep storage alive until cancellation completes.
                    self.kernel.GetOverlappedResult(self.handle,C.byref(pending),C.byref(transferred),True)
                    if waited==258:raise TimeoutError('IPC deadline')
                    raise BridgeFailure('WAIT_FAILED',number)
                if not self.kernel.GetOverlappedResult(self.handle,C.byref(pending),C.byref(transferred),False):raise self.native_failure()
            if not transferred.value:raise BridgeFailure('EOF')
            if data is not None:self.written+=transferred.value
            else:self.read_bytes+=transferred.value
            return transferred.value if data is not None else buffer.raw[:transferred.value]
        finally:self.kernel.CloseHandle(event)

    def read(self,count):
        result=bytearray()
        while len(result)<count:result.extend(self.io(count=count-len(result)))
        return bytes(result)

    def request(self,message,advanced=False):
        with self.lock:
            self.started=time.monotonic();self.trace=[];self.written=0;self.read_bytes=0;self.stage="PIPE_OPEN"
            self.peer_pid=None;self.pid_matches=None
            self.deadline=self.started+(2 if advanced else (.4 if message.get('op')=='status' else 3))
            kind='OK';number=0
            try:
                self.connect()
                self.mark('PIPE_WRITE')
                body=json.dumps(message,ensure_ascii=True,separators=(',',':')).encode('utf-8')
                if len(body)>8192:raise ValueError('Frame size')
                packet=struct.pack('!I',len(body))+body;offset=0
                while offset<len(packet):offset+=self.io(data=packet[offset:])
                self.mark('PIPE_READ_HEADER')
                count,=struct.unpack('!I',self.read(4))
                if not 1<=count<=8192:raise ValueError('Frame size')
                self.mark('PIPE_READ_BODY')
                response=json.loads(self.read(count))
                self.mark('PIPE_RESPONSE')
                if not isinstance(response,dict):raise ValueError('Frame type')
                if response.get('ok') is not True:
                    reason=response.get('reason')
                    raise BridgeFailure(reason if isinstance(reason,str) and reason in REASONS else 'BROKER_REJECTED')
                return response
            except (OSError,ValueError) as error:
                kind='TIMEOUT' if isinstance(error,TimeoutError) else getattr(error,'kind','INVALID_FRAME' if isinstance(error,ValueError) else 'IO_ERROR')
                number=error.errno if isinstance(error,OSError) and type(error.errno) is int else 0
                self.close();raise
            except BaseException:
                kind='INTERNAL_ERROR';self.close();raise
            finally:
                self.last_probe={'kind':kind,'stage':self.stage,'win32':max(0,number),'elapsed_ms':max(0,int((time.monotonic()-self.started)*1000)),
                    'written':self.written,'read':self.read_bytes,'peer_pid':self.peer_pid,'pid_matches':self.pid_matches,'trace':self.trace}

    def advanced(self):
        from desktop_checks import inspect_local
        with self.lock:
            previous=self.last_probe.copy()
            local=inspect_local()
            self.close() # Fresh identity check; no input or worker startup.
            try:self.request({'op':'status'},advanced=True)
            except (OSError,ValueError):pass
            return {'local':local,'previous':previous,'pipe':self.last_probe.copy()}

    def available(self):
        for _ in range(2):
            try:
                result=self.request({'op':'status'}).get('available') is True
                self.last_error='DESKTOP_OK' if result else 'DESKTOP_REJECTED'
                return result
            except (OSError,ValueError) as error:
                number=error.errno if isinstance(error,OSError) and type(error.errno) is int else 0
                self.last_error='DESKTOP_'+self.stage+'_'+str(number or 0)
        return False

    def execute(self,action):
        self.request({'op':'input','action':action})
