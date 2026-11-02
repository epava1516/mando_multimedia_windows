"""Mando protocol v1: pinned TLS, bounded JSON frames and explicit pairing. No remote shell."""
import argparse
from collections import deque
import datetime as dt
import hashlib
import ipaddress
import json
from pathlib import Path
import secrets
import socket
import socketserver
import ssl
import struct
import threading
import time

from windows_input import WindowsInput, DryRunInput, KEYS, MODIFIERS

MAX_FRAME = 8192
PORT = 1979


def integer(value, low, high):
    if type(value) is not int or not low <= value <= high:
        raise ValueError('Numero fuera de rango')
    return value


def validate_action(data):
    if not isinstance(data, dict):
        raise ValueError('Accion invalida')
    kind = data.get('type')
    if kind == 'key':
        key, mods = data.get('key'), data.get('modifiers', [])
        if not isinstance(key, str) or key not in KEYS or key in MODIFIERS:
            raise ValueError('Tecla no permitida')
        if (not isinstance(mods, list) or len(mods) > 4 or
                any(not isinstance(m, str) or m not in MODIFIERS for m in mods) or len(set(mods)) != len(mods)):
            raise ValueError('Modificadores invalidos')
        return {'type': kind, 'key': key, 'modifiers': mods}
    if kind == 'text':
        text = data.get('text')
        if not isinstance(text, str) or not 1 <= len(text) <= 500 or '\x00' in text:
            raise ValueError('Texto fuera de rango')
        text.encode('utf-16-le')
        return {'type': kind, 'text': text}
    if kind == 'move':
        return {'type': kind, 'dx': integer(data.get('dx'), -500, 500), 'dy': integer(data.get('dy'), -500, 500)}
    if kind == 'click' and data.get('button') in ('left', 'right'):
        return {'type': kind, 'button': data['button']}
    if kind in ('scroll', 'zoom'):
        return {'type': kind, 'steps': integer(data.get('steps'), -10 if kind == 'scroll' else -3, 10 if kind == 'scroll' else 3)}
    if kind in ('lock', 'task_view'):
        return {'type': kind}
    raise ValueError('Accion no permitida')


def exact_read(connection, count, deadline):
    parts = bytearray()
    while len(parts) < count:
        remaining = deadline-time.monotonic()
        if remaining <= 0: raise TimeoutError()
        connection.settimeout(remaining)
        block = connection.recv(count-len(parts))
        if not block:
            raise EOFError()
        parts.extend(block)
    return bytes(parts)


def receive(connection, timeout=5):
    deadline = time.monotonic()+timeout
    count, = struct.unpack('!I', exact_read(connection, 4, deadline))
    if not 1 <= count <= MAX_FRAME:
        raise ValueError('Frame fuera de rango')
    value = json.loads(exact_read(connection, count, deadline).decode('utf-8'),
                       parse_constant=lambda value: (_ for _ in ()).throw(ValueError('Numero invalido')))
    if not isinstance(value, dict):
        raise ValueError('Objeto JSON requerido')
    return value


def send(connection, value):
    data = json.dumps(value, ensure_ascii=True, allow_nan=False, separators=(',', ':')).encode('utf-8')
    if len(data) > MAX_FRAME:
        raise ValueError('Respuesta demasiado larga')
    connection.sendall(struct.pack('!I', len(data))+data)


class State:
    def __init__(self, adapter, clock=time.monotonic):
        self.adapter, self.clock = adapter, clock
        self.pin = f'{secrets.randbelow(100000000):08d}'
        self.pin_until = clock()+600
        self.attempts, self.actions = deque(maxlen=10), deque(maxlen=120)
        self.clients = 0
        self.guard = threading.Lock()
        self.input_guard = threading.Lock()

    def pair(self, pin):
        with self.guard:
            now = self.clock()
            if len(self.attempts) == 10 and now-self.attempts[0] < 60:
                return False
            self.attempts.append(now)
            if (now > self.pin_until or not isinstance(pin, str) or len(pin) != 8 or
                    not pin.isascii() or not pin.isdigit() or not secrets.compare_digest(pin, self.pin) or self.clients >= 4):
                return False
            self.clients += 1
            return True

    def rotate_pin(self):
        with self.guard:
            self.pin = f'{secrets.randbelow(100000000):08d}'
            self.pin_until = self.clock()+600
            self.attempts.clear()
            return self.pin

    def unpair(self):
        with self.guard:
            self.clients -= 1

    def execute(self, data):
        action = validate_action(data)
        with self.guard:
            now = self.clock()
            if len(self.actions) == 120 and now-self.actions[0] < 1:
                raise ValueError('Demasiadas acciones')
            self.actions.append(now)
        with self.input_guard:
            self.adapter.execute(action)

    def capabilities(self):
        return {'input': True, 'lock': True, 'task_view': True,
                'zoom': self.adapter.zoom_available(), 'secure_desktop': self.adapter.secure_desktop_available(), 'dry_run':isinstance(self.adapter,DryRunInput)}


class Handler(socketserver.BaseRequestHandler):
    def handle(self):
        paired, connection = False, None
        try:
            self.request.settimeout(5)
            connection = self.server.context.wrap_socket(self.request, server_side=True)
            send(connection, {'server':'mando', 'protocol':1})
            request = receive(connection)
            if request.get('op') != 'pair' or (type(request.get('protocol')) is not int or request.get('protocol') != 1) or not self.server.state.pair(request.get('pin')):
                send(connection, {'error':'Emparejamiento rechazado o caducado'})
                return
            paired = True
            send(connection, {'ok':True, 'protocol':1, 'capabilities':self.server.state.capabilities()})
            connection.settimeout(30)
            started, sequence = time.monotonic(), 0
            while time.monotonic()-started < 8*3600:
                request = receive(connection, 30)
                number = integer(request.get('id'), 1, 2147483647)
                if number != sequence+1:
                    raise ValueError('Secuencia invalida; no se repiten acciones')
                sequence = number
                operation = request.get('op')
                if operation == 'status':
                    response = {'ok':True, 'capabilities':self.server.state.capabilities()}
                elif operation == 'action':
                    try:
                        self.server.state.execute(request.get('action'))
                        response = {'ok':True}
                    except (ValueError, RuntimeError, OSError):
                        # Do not expose text, credentials, paths or arbitrary backend exceptions.
                        response = {'error':'Windows no ha aceptado la accion; no se repetira'}
                else:
                    raise ValueError('Operacion no permitida')
                send(connection, dict(response, id=number))
        except (OSError, ValueError, EOFError, UnicodeError, RecursionError):
            pass
        finally:
            if paired:
                self.server.state.unpair()
            if connection is not None:
                connection.close()


class Server(socketserver.ThreadingTCPServer):
    daemon_threads = True
    allow_reuse_address = True
    request_queue_size = 16

    def __init__(self, address, state, context):
        self.state, self.context = state, context
        self.slots = threading.BoundedSemaphore(16)
        super().__init__(address, Handler)

    def process_request(self, request, address):
        if not self.slots.acquire(blocking=False):
            self.shutdown_request(request)
            return
        try:
            super().process_request(request, address)
        except BaseException:
            self.slots.release()
            raise

    def process_request_thread(self, request, address):
        try:
            super().process_request_thread(request, address)
        finally:
            self.slots.release()

    def handle_error(self, request, address):
        pass  # Never dump incoming data or pairing secrets to console logs.


def identity(directory):
    from cryptography import x509
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.x509.oid import NameOID
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    key_path, cert_path = directory/'server-key.pem', directory/'server-cert.pem'
    if key_path.exists() != cert_path.exists():
        raise ValueError('Identidad TLS incompleta; restaura ambos archivos privados')
    if not key_path.exists():
        key = ec.generate_private_key(ec.SECP256R1())
        name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, 'Mando local server')])
        now = dt.datetime.now(dt.timezone.utc)
        cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(key.public_key())
                .serial_number(x509.random_serial_number()).not_valid_before(now-dt.timedelta(minutes=5))
                .not_valid_after(now+dt.timedelta(days=365)).sign(key, hashes.SHA256()))
        with key_path.open('xb') as output:
            key_path.chmod(0o600)
            output.write(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        cert_path.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    cert = x509.load_pem_x509_certificate(cert_path.read_bytes())
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain(str(cert_path), str(key_path))
    fingerprint = hashlib.sha256(cert.public_bytes(serialization.Encoding.DER)).hexdigest()
    return context, fingerprint


def private_ipv4(value, dry_run=False):
    address = ipaddress.IPv4Address(value)
    if address.is_loopback and dry_run:
        return str(address)
    if not any(address in ipaddress.ip_network(network) for network in ('10.0.0.0/8','172.16.0.0/12','192.168.0.0/16')):
        raise ValueError('Indica la IPv4 privada de una interfaz local')
    return str(address)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host', required=True)
    parser.add_argument('--port', type=int, default=PORT)
    parser.add_argument('--dry-run', action='store_true')
    arguments = parser.parse_args()
    host = private_ipv4(arguments.host, arguments.dry_run)
    integer(arguments.port, 1024, 65535)
    adapter = DryRunInput() if arguments.dry_run else WindowsInput()
    state = State(adapter)
    context, fingerprint = identity(Path(__file__).resolve().parent/'.state')
    with Server((host, arguments.port), state, context) as server:
        print('Mando servidor propio v1'+(' — SIMULACION SIN ENTRADA' if arguments.dry_run else ''), flush=True)
        def print_code():
            print('Copia este codigo en Mando (caduca en 10 minutos):', flush=True)
            print(f'mando1:{host}:{arguments.port}:{fingerprint}:{state.pin}', flush=True)
        def console():
            while True:
                try: command = input()
                except (EOFError, OSError): return
                if command.strip().lower() == 'r':
                    state.rotate_pin(); print_code()
        print_code()
        print('R y Enter genera otro codigo. Ctrl+C cierra las sesiones.', flush=True)
        print('No compartas el codigo fuera de tus dispositivos.', flush=True)
        threading.Thread(target=console,daemon=True).start()
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == '__main__':
    main()
