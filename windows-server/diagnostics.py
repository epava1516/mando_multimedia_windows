"""Bounded, memory-only diagnostic events. Never accepts user text or exceptions."""
from collections import deque
import datetime as dt
import threading

CODES = frozenset({'SERVER_STARTED','DEVICE_CONNECTED','DEVICE_DISCONNECTED','INPUT_REJECTED','CLIPBOARD_UNAVAILABLE'})
STAGES = frozenset({'NONE','SERVICE','TOKEN_OPEN','TOKEN_PRIVILEGE','TOKEN_DUPLICATE','WORKER_START','WORKER_CONNECT','WORKER_IDENTITY','WORKER_REPLY','SESSION_LOCK','INPUT_DESKTOP','SEND_INPUT','REQUEST'})

class Events:
    def __init__(self):
        self._items=deque(maxlen=24)
        self._lock=threading.Lock()

    def add(self, code):
        if code not in CODES: raise ValueError('Unknown diagnostic code')
        with self._lock:
            self._items.append({'time':dt.datetime.now(dt.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'),'code':code})

    def snapshot(self):
        with self._lock:return [dict(item) for item in self._items]


def desktop_status(adapter):
    bridge=getattr(adapter,'desktop_bridge',None)
    if bridge is None:return {'service_reachable':False,'probe':'NOT_AVAILABLE','worker_running':False,'worker_verified':False}
    try:
        response=bridge.request({'op':'status'})
        code=response.get('last_win32',0)
        stage=response.get('last_stage','NONE')
        return {'service_reachable':True,'probe':'OK','worker_running':response.get('worker_running') is True,
                'worker_verified':response.get('worker_verified') is True,
                'locked':response.get('locked') is True,
                'last_stage':stage if isinstance(stage,str) and stage in STAGES else 'REQUEST',
                'last_win32':code if type(code) is int and 0<=code<=2147483647 else 0}
    except (OSError,ValueError) as error:
        number=getattr(error,"winerror",None) or getattr(error,"errno",None)
        number=number if type(number) is int and 0<=number<=2147483647 else 0
        stage=getattr(bridge,'stage','')
        return {'service_reachable':False,'probe':stage if stage in PIPE_STAGES else 'PIPE_ERROR',
                'last_stage':stage if stage in PIPE_STAGES else 'REQUEST',
                'last_win32':number,'worker_running':False,'worker_verified':False}


PIPE_STAGES=frozenset({'PIPE_OPEN','PIPE_PEER_PID','SCM_OPEN','SERVICE_OPEN','SERVICE_STATUS','SERVICE_IDENTITY','PIPE_WRITE','PIPE_READ_HEADER','PIPE_READ_BODY','PIPE_RESPONSE','PIPE_STATUS','PIPE_INPUT'})
PIPE_KINDS=frozenset({'OK','WIN32','TIMEOUT','WAIT_FAILED','EOF','IDENTITY_MISMATCH','INVALID_FRAME','IO_ERROR','INTERNAL_ERROR','BROKER_REJECTED','NO_CONSOLE','CLIENT_SESSION_QUERY','CLIENT_SESSION_MISMATCH','CLIENT_IDENTITY','CLIENT_ACCOUNT_MISMATCH'})
LOCAL_ENUMS={'service_state':{'UNKNOWN','STOPPED','START_PENDING','STOP_PENDING','RUNNING','CONTINUE_PENDING','PAUSE_PENDING','PAUSED'},'service_account':{'UNKNOWN','LOCAL_SYSTEM','OTHER'},'allowed_sid_file':{'UNKNOWN','OK','MISSING','INVALID','READ_FAILED'}}

def bounded_number(value):
    return value if type(value) is int and 0<=value<=2147483647 else 0

def safe_enum(value, choices, default="UNKNOWN"):
    return value if isinstance(value,str) and value in choices else default

def clean_probe(raw):
    raw=raw if isinstance(raw,dict) else {}
    result={'kind':safe_enum(raw.get('kind'),PIPE_KINDS),
            'stage':safe_enum(raw.get('stage'),PIPE_STAGES)}
    for key in ('win32','elapsed_ms','written','read','peer_pid'):result[key]=bounded_number(raw.get(key))
    result['pid_matches']=raw.get('pid_matches') if type(raw.get('pid_matches')) is bool else None
    trace=raw.get('trace',[])
    result['trace']=[{'stage':safe_enum(item.get('stage'),PIPE_STAGES),'at_ms':bounded_number(item.get('at_ms'))}
        for item in trace[:16] if isinstance(item,dict)] if isinstance(trace,list) else []
    return result

def advanced_status(adapter):
    bridge=getattr(adapter,'desktop_bridge',None)
    if bridge is None:return {'available':False}
    raw=bridge.advanced();raw=raw if isinstance(raw,dict) else {}
    local=raw.get('local',{});local=local if isinstance(local,dict) else {}
    clean={key:safe_enum(local.get(key),choices) for key,choices in LOCAL_ENUMS.items()}
    for key in ('service_pid','service_error','identity_error'):clean[key]=bounded_number(local.get(key))
    for key in ('client_session','console_session'):clean[key]=local.get(key) if type(local.get(key)) is int and 0<=local[key]<=2147483647 else None
    for key in ('account_match','session_match'):clean[key]=local.get(key) if type(local.get(key)) is bool else None
    return {'available':True,'local':clean,'previous':clean_probe(raw.get('previous')),'pipe':clean_probe(raw.get('pipe'))}
