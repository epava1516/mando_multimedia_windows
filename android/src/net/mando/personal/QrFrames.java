package net.mando.personal;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

final class QrFrames {
    static String decode(byte[] data,int width,int height) {
        if(data==null||width<1||height<1||width>1920||height>1920||(long)width*height>2073600||data.length<(long)width*height)return null;
        try {
            PlanarYUVLuminanceSource source=new PlanarYUVLuminanceSource(data,width,height,0,0,width,height,false);
            return PairingCode.validate(new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source))).getText());
        }catch(Exception invalid){return null;}
    }
}
