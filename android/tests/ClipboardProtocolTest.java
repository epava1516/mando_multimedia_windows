package net.mando.personal;
import java.io.*;
import java.util.Arrays;
public final class ClipboardProtocolTest {
    static byte[] hex(String s){byte[] out=new byte[s.length()/2];for(int i=0;i<out.length;i++)out[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return out;}
    static void rejects(byte[] body,byte[] key)throws Exception {
        try{ClipboardProtocol.read(new DataInputStream(new ByteArrayInputStream(body)),key);throw new AssertionError("must reject");}catch(IOException expected){}
    }
    public static void main(String[] args)throws Exception {
        byte[] key={1,2,3,4,5,6,7,8};
        byte[] vector=hex("23000040401e0000005a5a521aa60e831b00000000000000007a5b58595e5f5c5d52626e732977");
        if(!Arrays.equals(vector,ClipboardProtocol.request(null,1700000000.125,(byte)0x5a,key)))throw new AssertionError("request vector");
        String text=ClipboardProtocol.read(new DataInputStream(new ByteArrayInputStream(hex("74787403000000404040"))),key);
        if(!text.equals("ABC"))throw new AssertionError("response vector");
        rejects(hex("696d6700000000"),key); // Images aren't text.
        rejects(hex("747874a10f0000"),key); // Bound before allocating.
        rejects(hex("747874ffffffff"),key);
        rejects(hex("74787401000000fe"),key); // Invalid UTF8 after XOR.
        rejects(hex("7478740300000040"),key); // Truncation.
        for(String invalid:new String[]{"x\\0y".replace("\\0","\0"),new String(new char[1001]).replace('\0','x'),"\ud800"}){
            try{ClipboardProtocol.request(invalid,1700000000,(byte)1,key);throw new AssertionError("invalid text");}catch(IOException expected){}
        }
        System.out.println("Clipboard codec vectors, types, bounds, UTF8 and truncation: passed");
    }
}
