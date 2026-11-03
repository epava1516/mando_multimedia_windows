"""Local discovery advertises only service presence and TCP port, never pairing credentials."""
import ipaddress
import re
import socket
import threading
import time

PORT=1980
REQUEST=re.compile(rb'MANDO_DISCOVER_V1 ([a-f0-9]{32})\Z')


def response(data, tcp_port):
    match=REQUEST.fullmatch(data)
    if not match or type(tcp_port) is not int or not 1024<=tcp_port<=65535:return None
    return b'MANDO_SERVER_V1 '+match[1]+b' '+str(tcp_port).encode('ascii')


class Discovery:
    def __init__(self, tcp_port, port=PORT):
        self.tcp_port=tcp_port
        self.socket=socket.socket(socket.AF_INET,socket.SOCK_DGRAM)
        self.socket.bind(('',port));self.socket.settimeout(.5)
        self.stopped=threading.Event()
        self.thread=threading.Thread(target=self.run,daemon=True)

    def start(self):self.thread.start()

    def run(self):
        next_allowed=0
        while not self.stopped.is_set():
            try:
                data,peer=self.socket.recvfrom(128)
                if not ipaddress.ip_address(peer[0]).is_private:continue
                answer=response(data,self.tcp_port)
                now=time.monotonic()
                if answer is None or now<next_allowed:continue
                next_allowed=now+.05
                self.socket.sendto(answer,peer)
            except socket.timeout:continue
            except OSError:break

    def close(self):
        self.stopped.set();self.socket.close()
        if self.thread.is_alive():self.thread.join(timeout=1)
