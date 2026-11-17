"""Fixed read-only Win32 checks; never executes commands or returns account names/SIDs."""
import ctypes as C
from ctypes import wintypes as W
import os
from pathlib import Path
import re


def inspect_local():
    result={'service_state':'UNKNOWN','service_account':'UNKNOWN','service_pid':0,'service_error':0,
            'allowed_sid_file':'UNKNOWN','account_match':None,'client_session':None,'console_session':None,'session_match':None,'identity_error':0}
    kernel=C.WinDLL('kernel32',use_last_error=True)
    security=C.WinDLL('advapi32',use_last_error=True)
    wts=C.WinDLL('kernel32',use_last_error=True)
    kernel.GetCurrentProcess.restype=W.HANDLE
    kernel.GetCurrentProcessId.restype=W.DWORD
    kernel.ProcessIdToSessionId.argtypes=[W.DWORD,C.POINTER(W.DWORD)]
    kernel.CloseHandle.argtypes=[W.HANDLE]
    kernel.LocalFree.argtypes=[C.c_void_p]
    wts.WTSGetActiveConsoleSessionId.restype=W.DWORD
    session=W.DWORD();console=wts.WTSGetActiveConsoleSessionId()
    if console!=0xffffffff:result['console_session']=console
    if kernel.ProcessIdToSessionId(kernel.GetCurrentProcessId(),C.byref(session)):
        result['client_session']=session.value
        if console!=0xffffffff:result['session_match']=session.value==console
    else:result['identity_error']=C.get_last_error()
    security.OpenSCManagerW.argtypes=[W.LPCWSTR,W.LPCWSTR,W.DWORD];security.OpenSCManagerW.restype=W.HANDLE
    security.OpenServiceW.argtypes=[W.HANDLE,W.LPCWSTR,W.DWORD];security.OpenServiceW.restype=W.HANDLE
    security.CloseServiceHandle.argtypes=[W.HANDLE]
    security.QueryServiceStatusEx.argtypes=[W.HANDLE,C.c_int,C.c_void_p,W.DWORD,C.POINTER(W.DWORD)]
    security.QueryServiceConfigW.argtypes=[W.HANDLE,C.c_void_p,W.DWORD,C.POINTER(W.DWORD)]
    class CONFIG(C.Structure):
        _fields_=[('type',W.DWORD),('start',W.DWORD),('error',W.DWORD),('binary',W.LPWSTR),('group',W.LPWSTR),('tag',W.DWORD),('dependencies',W.LPWSTR),('account',W.LPWSTR),('display',W.LPWSTR)]
    manager=service=None
    try:
        manager=security.OpenSCManagerW(None,None,1)
        if not manager:raise OSError(C.get_last_error(),'SCM')
        service=security.OpenServiceW(manager,'MandoDesktopBroker',5) # QUERY_CONFIG | QUERY_STATUS only.
        if not service:raise OSError(C.get_last_error(),'SERVICE')
        status=(W.DWORD*9)();needed=W.DWORD()
        if not security.QueryServiceStatusEx(service,0,status,C.sizeof(status),C.byref(needed)):raise OSError(C.get_last_error(),'STATUS')
        result['service_state']={1:'STOPPED',2:'START_PENDING',3:'STOP_PENDING',4:'RUNNING',5:'CONTINUE_PENDING',6:'PAUSE_PENDING',7:'PAUSED'}.get(status[1],'UNKNOWN')
        result['service_pid']=status[7]
        security.QueryServiceConfigW(service,None,0,C.byref(needed))
        if not 1<=needed.value<=65536:raise OSError(C.get_last_error(),'CONFIG')
        buffer=C.create_string_buffer(needed.value)
        if not security.QueryServiceConfigW(service,buffer,len(buffer),C.byref(needed)):raise OSError(C.get_last_error(),'CONFIG')
        config=C.cast(buffer,C.POINTER(CONFIG)).contents
        account=config.account or ''
        result['service_account']='LOCAL_SYSTEM' if account.lower() in ('localsystem','nt authority\\system') else 'OTHER'
    except OSError as error:result['service_error']=max(0,error.errno or 0)
    finally:
        if service:security.CloseServiceHandle(service)
        if manager:security.CloseServiceHandle(manager)

    # One fixed installer-owned SID file. Its content never leaves this function.
    expected=None
    root=os.environ.get('ProgramW6432') or os.environ.get('ProgramFiles')
    if not root:result['allowed_sid_file']='MISSING'
    else:
        path=Path(root)/'MandoDesktop'/'allowed.sid'
        try:
            with path.open('rb') as stream:raw=stream.read(513)
            value=raw.decode('utf-8-sig').strip()
            if len(raw)>512 or not re.fullmatch(r'S-\d{1,3}(?:-\d{1,20}){1,15}',value):result['allowed_sid_file']='INVALID'
            else:expected=value;result['allowed_sid_file']='OK'
        except FileNotFoundError:result['allowed_sid_file']='MISSING'
        except (OSError,UnicodeError):result['allowed_sid_file']='READ_FAILED'
    token=W.HANDLE()
    security.OpenProcessToken.argtypes=[W.HANDLE,W.DWORD,C.POINTER(W.HANDLE)]
    security.GetTokenInformation.argtypes=[W.HANDLE,C.c_int,C.c_void_p,W.DWORD,C.POINTER(W.DWORD)]
    security.ConvertSidToStringSidW.argtypes=[C.c_void_p,C.POINTER(W.LPWSTR)]
    try:
        if not security.OpenProcessToken(kernel.GetCurrentProcess(),8,C.byref(token)):raise OSError(C.get_last_error(),'TOKEN')
        needed=W.DWORD();security.GetTokenInformation(token,1,None,0,C.byref(needed))
        if not 1<=needed.value<=65536:raise OSError(C.get_last_error(),'TOKEN_USER')
        buffer=C.create_string_buffer(needed.value)
        if not security.GetTokenInformation(token,1,buffer,len(buffer),C.byref(needed)):raise OSError(C.get_last_error(),'TOKEN_USER')
        sid=C.cast(buffer,C.POINTER(C.c_void_p))[0];text=W.LPWSTR()
        if not security.ConvertSidToStringSidW(sid,C.byref(text)):raise OSError(C.get_last_error(),'SID')
        try:
            if expected is not None:result['account_match']=text.value==expected
        finally:kernel.LocalFree(C.cast(text,C.c_void_p))
    except OSError as error:result['identity_error']=max(0,error.errno or 0)
    finally:
        if token:kernel.CloseHandle(token)
    return result
