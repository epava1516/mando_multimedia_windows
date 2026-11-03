package net.mando.personal;
import java.io.IOException;

/** Only a bounded private-network Mando pairing code; never an executable URL. */
final class PairingCode {
    static String validate(String value)throws IOException {
        if(value==null||value.length()>140||!value.matches("mando1:[0-9.]{7,15}:[0-9]{4,5}:[a-fA-F0-9]{64}:[0-9]{8}"))
            throw new IOException("El QR no contiene un código Mando válido");
        String[] fields=value.split(":"),parts=fields[1].split("\\.");
        if(parts.length!=4)throw new IOException("Dirección del QR inválida");
        int[] ip=new int[4];
        try{for(int i=0;i<4;i++){ip[i]=Integer.parseInt(parts[i]);if(ip[i]>255)throw new NumberFormatException();}}
        catch(NumberFormatException invalid){throw new IOException("Dirección del QR inválida");}
        if(!(ip[0]==10||(ip[0]==172&&ip[1]>=16&&ip[1]<=31)||(ip[0]==192&&ip[1]==168)))
            throw new IOException("El QR debe apuntar a un PC de la red privada");
        int port=Integer.parseInt(fields[2]);
        if(port<1024||port>65535)throw new IOException("Puerto del QR inválido");
        return value;
    }
}
