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


def main(executable, real_no_input=False):
    host=socket.gethostbyname(socket.gethostname()) if real_no_input else '127.0.0.1'
    probe=socket.socket();probe.bind((host,0));port=probe.getsockname()[1];probe.close()
    arguments=[executable,'--host',host,'--port',str(port)]+([] if real_no_input else ['--dry-run'])
    process=subprocess.Popen(arguments,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
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
        with context.wrap_socket(socket.create_connection((host,port),timeout=5),server_hostname='mando') as connection:
            assert hashlib.sha256(connection.getpeercert(binary_form=True)).hexdigest()==code[3]
            assert receive(connection)['protocol']==1
            send(connection,{'op':'pair','protocol':1,'pin':code[4]})
            reply=receive(connection)
            assert reply['capabilities']['dry_run'] is (not real_no_input)
            if real_no_input:
                assert reply['capabilities']['secure_desktop'] is False
                send(connection,{'op':'status','id':1})
                assert receive(connection)['ok'] is True
            else:
                send(connection,{'op':'action','id':1,'action':{'type':'lock'}})
                assert receive(connection)=={'ok':True,'id':1}
        process.stdin.write('STOP\n');process.stdin.flush();process.wait(timeout=10)
        assert process.returncode==0,'Frozen server failed to stop cleanly'
        print('Standalone EXE: normal desktop without broker, TLS pairing/status, no input: passed' if real_no_input else 'Standalone EXE: pinned TLS, pairing, simulated action and shutdown passed')
    finally:
        if process.poll() is None:
            process.kill();process.wait(timeout=5)


if __name__=='__main__':main(sys.argv[1], '--real-no-input' in sys.argv[2:])
