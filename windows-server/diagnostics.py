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
                'last_stage':stage if stage in STAGES else 'REQUEST',
                'last_win32':code if type(code) is int and 0<=code<=2147483647 else 0}
    except (OSError,ValueError) as error:
        number=getattr(error,"winerror",None) or getattr(error,"errno",None)
        number=number if type(number) is int and 0<=number<=2147483647 else 0
        stage=getattr(bridge,'stage','')
        return {'service_reachable':False,'probe':stage if stage in ('PIPE_OPEN','SERVICE_IDENTITY','PIPE_STATUS','PIPE_INPUT') else 'PIPE_ERROR',
                'last_stage':stage if stage in ('PIPE_OPEN','SERVICE_IDENTITY','PIPE_STATUS','PIPE_INPUT') else 'REQUEST',
                'last_win32':number,'worker_running':False,'worker_verified':False}
