package net.mando.personal;

import android.net.Network;
import org.json.JSONObject;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

/** Mando v1: exact certificate pin from the PC's pairing code; no trust-all fallback or retries. */
final class OwnClient implements InputClient {
    private volatile Socket socket;
    private volatile boolean ready;
    private DataInputStream input;
    private DataOutputStream output;
    private int sequence;
    private JSONObject capabilities = new JSONObject();

    public boolean isConnected() {Socket current=socket;return ready && current!=null && !current.isClosed();}
    public void close() {
        ready=false;Socket previous=socket;socket=null;
        if(previous!=null)try{previous.close();}catch(IOException ignored){ }
    }
    boolean supportsHorizontalScroll(){return capabilities.optBoolean("horizontal_scroll",false);}
    boolean supportsSecureDesktop() {return capabilities.optBoolean("secure_desktop",false);}
    boolean isDryRun() {return capabilities.optBoolean("dry_run",false);}
    boolean supportsZoom() {return capabilities.optBoolean("zoom",false);}

    JSONObject connect(Network network, String code, JSONObject saved) throws Exception {
        JSONObject profile;
        JSONObject authentication;
        if(code!=null && !code.isEmpty()) {
            if(!code.matches("mando1:[0-9.]{7,15}:[0-9]{4,5}:[a-fA-F0-9]{64}:[0-9]{8}"))
                throw new IOException("Pega el código mando1 completo que muestra tu PC");
            String[] fields=code.split(":");
            profile=new JSONObject().put("host",fields[1]).put("port",Integer.parseInt(fields[2])).put("fingerprint",fields[3]);
            authentication=new JSONObject().put("op","pair").put("protocol",1).put("pin",fields[4]);
        } else {
            if(saved==null)throw new IOException("Introduce el código del PC la primera vez");
            profile=new JSONObject(saved.toString());
            if(!profile.getString("id").matches("[a-f0-9]{32}")||!profile.getString("token").matches("[A-Za-z0-9_-]{43}"))throw new IOException("Dispositivo guardado inválido");
            authentication=new JSONObject().put("op","resume").put("protocol",1).put("device",profile.getString("id")).put("token",profile.getString("token"));
        }
        String host=profile.getString("host"),fingerprint=profile.getString("fingerprint");
        InetAddress address=RemoteClient.privateAddress(host);
        int port=profile.getInt("port");
        if(port<1024||port>65535||!fingerprint.matches("[a-fA-F0-9]{64}"))throw new IOException("Identidad propia inválida");
        final byte[] pin=new byte[32];
        for(int i=0;i<32;i++)pin[i]=(byte)Integer.parseInt(fingerprint.substring(i*2,i*2+2),16);
        SSLContext context=SSLContext.getInstance("TLS");
        context.init(null,new TrustManager[]{new PinnedTrust(pin)},null);
        close();Socket raw=new Socket();socket=raw;
        try {
            network.bindSocket(raw);raw.setTcpNoDelay(true);raw.setSoTimeout(5000);
            raw.connect(new InetSocketAddress(address,port),3000);
            SSLSocket tls=(SSLSocket)context.getSocketFactory().createSocket(raw,host,port,true);
            if(socket!=raw){tls.close();throw new IOException("Conexión cancelada");}
            socket=tls;tls.setSoTimeout(5000);java.util.List<String> protocols=new java.util.ArrayList<>();
            for(String protocol:tls.getSupportedProtocols())if("TLSv1.2".equals(protocol)||"TLSv1.3".equals(protocol))protocols.add(protocol);
            tls.setEnabledProtocols(protocols.toArray(new String[0]));tls.startHandshake();
            input=new DataInputStream(tls.getInputStream());output=new DataOutputStream(tls.getOutputStream());
            JSONObject greeting=read();
            if(!"mando".equals(greeting.optString("server")) || greeting.optInt("protocol")!=1)throw new IOException("Servidor propio incompatible");
            write(authentication);
            JSONObject reply=read();
            if(!reply.optBoolean("ok") || reply.optInt("protocol")!=1)throw new IOException("Código caducado o emparejamiento rechazado en el PC");
            capabilities=reply.getJSONObject("capabilities");sequence=0;
            if(socket!=tls)throw new IOException("Conexión cancelada");
            if(reply.has("device")) {
                JSONObject device=reply.getJSONObject("device");
                if(!device.getString("id").matches("[a-f0-9]{32}")||!device.getString("token").matches("[A-Za-z0-9_-]{43}"))throw new IOException("Autorización propia inválida");
                profile.put("id",device.getString("id")).put("token",device.getString("token"));
            }
            if(!profile.has("token"))throw new IOException("Actualiza el servidor propio para guardar el dispositivo");
            ready=true;return profile;
        }catch(Exception error){close();throw error;}
    }
    private JSONObject read() throws Exception {
        int length=input.readInt();
        if(length<1 || length>8192)throw new IOException("Respuesta propia fuera de rango");
        byte[] body=new byte[length];input.readFully(body);
        return new JSONObject(new String(body,StandardCharsets.UTF_8));
    }
    private void write(JSONObject data)throws Exception {
        byte[] body=data.toString().getBytes(StandardCharsets.UTF_8);
        if(body.length>8192)throw new IOException("Mensaje demasiado largo");
        output.writeInt(body.length);output.write(body);output.flush();
    }
    private synchronized JSONObject request(String operation,JSONObject data)throws Exception {
        if(!isConnected())throw new IOException("Vuelve a conectar al servidor propio");
        try {
            int id=++sequence;
            JSONObject request=new JSONObject().put("op",operation).put("id",id);
            if(data!=null)request.put("action",data);
            write(request);JSONObject reply=read();
            if(reply.optInt("id")!=id)throw new IOException("Secuencia propia inválida");
            if(!reply.optBoolean("ok"))throw new IOException("El servidor propio no ha aceptado la acción; no se repetirá");
            return reply;
        }catch(Exception error){close();throw error;}
    }
    void refreshStatus() throws Exception {capabilities=request("status",null).getJSONObject("capabilities");}
    public void action(JSONObject data)throws Exception {request("action",data);}
}
