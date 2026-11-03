import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import socket
import unittest
from discovery import Discovery,response

class DiscoveryTests(unittest.TestCase):
    def test_request_is_bounded_and_contains_no_credentials(self):
        nonce=b'a'*32
        answer=response(b'MANDO_DISCOVER_V1 '+nonce,1979)
        self.assertEqual(answer,b'MANDO_SERVER_V1 '+nonce+b' 1979')
        for data in [b'',b'MANDO_DISCOVER_V1 '+b'x'*32,b'MANDO_DISCOVER_V1 '+nonce+b'\n',b'R',b'clp-r']:
            self.assertIsNone(response(data,1979))
        self.assertIsNone(response(b'MANDO_DISCOVER_V1 '+nonce,80))

    def test_socket_discovers_without_authenticating_or_executing_input(self):
        discovery=Discovery(1979,port=0);port=discovery.socket.getsockname()[1]
        discovery.start()
        try:
            with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as client:
                client.settimeout(2)
                client.sendto(b'MANDO_DISCOVER_V1 '+b'1'*32,('127.0.0.1',port))
                self.assertEqual(client.recv(128),b'MANDO_SERVER_V1 '+b'1'*32+b' 1979')
        finally:discovery.close()
