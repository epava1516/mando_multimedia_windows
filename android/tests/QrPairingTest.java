package net.mando.personal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;

public final class QrPairingTest {
    static final String CODE="mando1:192.168.1.10:1979:"+new String(new char[64]).replace('\0','a')+":12345678";
    public static void main(String[] args)throws Exception {
        if(!PairingCode.validate(CODE).equals(CODE))throw new AssertionError("valid code");
        for(String invalid:new String[]{"https://example.invalid",CODE.replace("192.168.1.10","8.8.8.8"),CODE.replace("1979","99999"),CODE.replace("192.168.1.10","192.168.999.1"),CODE+"\n"}) {
            try{PairingCode.validate(invalid);throw new AssertionError("accepts invalid code");}catch(IOException expected){}
        }
        byte[] image=Files.readAllBytes(Paths.get(args[0]));int width=640,height=480;
        for(int turn=0;turn<4;turn++) {
            if(!CODE.equals(QrFrames.decode(image,width,height)))throw new AssertionError("QR roundtrip orientation "+turn);
            byte[] rotated=new byte[image.length];
            for(int y=0;y<height;y++)for(int x=0;x<width;x++)rotated[x*height+(height-1-y)]=image[y*width+x];
            image=rotated;int previous=width;width=height;height=previous;
        }
        if(QrFrames.decode(new byte[1],640,480)!=null)throw new AssertionError("truncated frame");
        if(QrFrames.decode(new byte[640*480],640,480)!=null)throw new AssertionError("blank frame");
        System.out.println("Python QR to Android ZXing: four orientations, private pairing validation and invalid frames passed");
    }
}
