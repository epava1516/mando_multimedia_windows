package net.mando.personal;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Official Dock's bounded text-only codec. Obfuscation is not TLS. */
final class ClipboardProtocol {
    static byte[] request(String text, double timestamp, byte mask, byte[] key) throws IOException {
        if(key.length!=8 || !Double.isFinite(timestamp))throw new IOException("Formato inválido");
        byte[] plain;
        if(text==null)plain="clp-r".getBytes(StandardCharsets.US_ASCII);
        else {
            validate(text);
            byte[] body=text.getBytes(StandardCharsets.UTF_8);
            plain=ByteBuffer.allocate(12+body.length).order(ByteOrder.LITTLE_ENDIAN)
                .put("clp-wtxt".getBytes(StandardCharsets.US_ASCII)).putInt(body.length).put(body).array();
        }
        byte[] time=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(timestamp).array();
        ByteBuffer out=ByteBuffer.allocate(34+plain.length).order(ByteOrder.LITTLE_ENDIAN);
        out.put((byte)'#').putFloat(3.0f).putInt(25+plain.length);
        for(byte value:time)out.put((byte)(value^mask));
        out.putInt(0).putInt(0).put((byte)(mask^0x20));
        for(byte value:key)out.put((byte)(value^mask));
        for(int i=0;i<plain.length;i++)out.put((byte)(plain[i]^key[i%8]));
        return out.array();
    }
    static String validate(String text)throws IOException {
        if(text==null || text.length()>1000 || text.indexOf(0)>=0)throw new IOException("Portapapeles fuera de rango");
        try {StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text));}
        catch(CharacterCodingException error){throw new IOException("Texto inválido");}
        return text;
    }
    static String read(DataInputStream input,byte[] key)throws IOException {
        byte[] tag=new byte[3];input.readFully(tag);
        int size=Integer.reverseBytes(input.readInt());
        String type=new String(tag,StandardCharsets.US_ASCII);
        if(size<0 || size>4000)throw new IOException("Portapapeles fuera de rango");
        if("emp".equals(type)&&size==0)return "";
        if(!"txt".equals(type))throw new IOException("El portapapeles del PC no contiene texto compatible");
        byte[] body=new byte[size];input.readFully(body);
        for(int i=0;i<size;i++)body[i]^=key[i%8];
        try {
            String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body)).toString();
            return validate(text);
        }catch(CharacterCodingException error){throw new IOException("Respuesta de portapapeles inválida");}
    }
}
