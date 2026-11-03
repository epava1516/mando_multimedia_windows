"""Local matrix-only QR encoding: no files, images, remote services or logs."""
import re
import qrcode


def frame(code):
    if not isinstance(code,str) or not re.fullmatch(r'mando1:[0-9.]{7,15}:[0-9]{4,5}:[a-f0-9]{64}:[0-9]{8}',code):
        raise ValueError('Codigo no valido')
    qr=qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M,border=4)
    qr.add_data(code);qr.make(fit=True)
    matrix=qr.get_matrix();width=len(matrix)
    if width>97:raise ValueError('QR fuera de rango')
    return 'MANDO_QR '+str(width)+' '+''.join('1' if bit else '0' for row in matrix for bit in row)
