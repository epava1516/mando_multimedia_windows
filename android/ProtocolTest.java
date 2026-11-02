package net.mando.personal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class ProtocolTest {
    static void expect(byte[] actual,String expected) {
        if(!Arrays.equals(actual,expected.getBytes(StandardCharsets.UTF_8)))
            throw new AssertionError("Frame incorrecto: "+new String(actual,StandardCharsets.UTF_8));
    }
    public static void main(String[] args) {
        expect(Protocol.text("hola"),"key  9[noe]hola");
        expect(Protocol.text("ñ"),"key  7[noe]ñ");
        expect(Protocol.text("😀"),"key  9[noe]😀");
        expect(Protocol.frame("key","F12"),"key  3F12");
        expect(Protocol.frame("key","[kld]a[*]ctrl[*]shift"),"key 21[kld]a[*]ctrl[*]shift");
        expect(Protocol.frame("mos","R l d"),"mos  5R l d");
        expect(Protocol.systemMedia("media_play_pause"),"key 24[kld]media_play_pause[*]");
        expect(Protocol.systemMedia("volume_up"),"key 17[kld]volume_up[*]");
        expect(Protocol.systemMedia("volume_down"),"key 19[kld]volume_down[*]");
        expect(Protocol.systemMedia("volume_mute"),"key 19[kld]volume_mute[*]");
        expect(Protocol.navigationKey("space"),"key 13[kld]space[*]");
        expect(Protocol.navigationKey("rt"),"key 10[kld]rt[*]");
        expect(Protocol.frame("key","[kld]l[*]ctrl[*]alt"),"key 19[kld]l[*]ctrl[*]alt");
        expect(Protocol.zoom(true),"key 13[kld]+[*]ctrl");
        expect(Protocol.zoom(false),"key 13[kld]-[*]ctrl");
        expect(Protocol.move(1,2),"mos 15m 1.0000 2.0000");
        expect(Protocol.move(-1,-2),"mos 17m -1.0000 -2.0000");
        try {Protocol.text(new String(new char[995]).replace('\0','a'));throw new AssertionError("Aceptó longitud excesiva");}
        catch(IllegalArgumentException expected) { }
        try {Protocol.systemMedia("not_a_media_key");throw new AssertionError("Aceptó tecla arbitraria");}
        catch(IllegalArgumentException expected) { }
        try {Protocol.navigationKey("arbitrary");throw new AssertionError("Aceptó tecla arbitraria");}
        catch(IllegalArgumentException expected) { }
        System.out.println("20 comprobaciones de protocolo correctas");
    }
}
