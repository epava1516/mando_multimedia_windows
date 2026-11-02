"""Runs only the frozen server in dry-run; no service installation or real host input."""
import hashlib
import os
from pathlib import Path
import queue
import socket
import ssl
import subprocess
import sys
import threading

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from server import receive,send


def main(executable):
    probe=socket.socket();probe.bind(('127.0.0.1',0));port=probe.getsockname()[1];probe.close()
    process=subprocess.Popen([executable,'--host','127.0.0.1','--port',str(port),'--dry-run'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
    lines=queue.Queue()
    threading.Thread(target=lambda:[lines.put(line.strip()) for line in process.stdout],daemon=True).start()
    try:
        code=None
        for _ in range(6):
            line=lines.get(timeout=30)
            if line.startswith('mando1:'):code=line.split(':');break
        assert code is not None,'No pairing code from executable'
        context=ssl.create_default_context(cafile=str(Path(os.environ['LOCALAPPDATA'])/'MandoServer'/'server-cert.pem'))
        context.check_hostname=False
        with context.wrap_socket(socket.create_connection(('127.0.0.1',port),timeout=5),server_hostname='mando') as connection:
            assert hashlib.sha256(connection.getpeercert(binary_form=True)).hexdigest()==code[3]
            assert receive(connection)['protocol']==1
            send(connection,{'op':'pair','protocol':1,'pin':code[4]})
            assert receive(connection)['capabilities']['dry_run'] is True
            send(connection,{'op':'action','id':1,'action':{'type':'lock'}})
            assert receive(connection)=={'ok':True,'id':1}
        process.stdin.write('STOP\n');process.stdin.flush();process.wait(timeout=10)
        assert process.returncode==0,'Frozen server failed to stop cleanly'
        print('Standalone EXE: pinned TLS, pairing, simulated action and shutdown passed')
    finally:
        if process.poll() is None:
            process.kill();process.wait(timeout=5)


if __name__=='__main__':main(sys.argv[1])
