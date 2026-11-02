package net.mando.personal;

import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class RemoteClient {
    static final int PORT = 1978;
    private volatile Socket socket;
    private volatile DatagramSocket commandSocket;
    private volatile boolean ready;
    private int version;
    private static final Map<String,String> KEYS = new HashMap<>();
    static {
        String[][] pairs = {{"ESC","esc"},{"TAB","tab"},{"ENTER","RTN"},{"BACKSPACE","BAS"},
            {"DELETE","delete"},{"INSERT","insert"},{"HOME","home"},{"END","end"},
            {"PAGEUP","pageup"},{"PAGEDOWN","pagedown"},{"LEFT","LF"},{"RIGHT","RT"},
            {"UP","UP"},{"DOWN","DW"},{"SPACE","space"}};
        for (String[] pair:pairs) KEYS.put(pair[0],pair[1]);
        for(int i=1;i<=12;i++) KEYS.put("F"+i,"F"+i);
        for(char c='A';c<='Z';c++) KEYS.put(String.valueOf(c),String.valueOf(Character.toLowerCase(c)));
        for(char c='0';c<='9';c++) KEYS.put(String.valueOf(c),String.valueOf(c));
    }

    boolean isConnected() { Socket s=socket; return ready && s!=null && !s.isClosed(); }
    int serverVersion() { return version; }

    void close() {
        ready=false;
        DatagramSocket previousCommand=commandSocket;commandSocket=null;
        if(previousCommand!=null)previousCommand.close();
        Socket previous=socket; socket=null;
        if(previous!=null) try {previous.close();} catch(IOException ignored) { }
    }

    static InetAddress privateAddress(String host) throws IOException {
        if(host==null || !host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) throw new IOException("Introduce una dirección IPv4 privada válida");
        String[] fields=host.split("\\."); byte[] bytes=new byte[4];
        for(int i=0;i<4;i++) {
            int part=Integer.parseInt(fields[i]);
            if(part>255) throw new IOException("Dirección IPv4 inválida");
            bytes[i]=(byte)part;
        }
        int first=bytes[0]&255, second=bytes[1]&255;
        if(!(first==10 || (first==172 && second>=16 && second<=31) || (first==192 && second==168)))
            throw new IOException("Selecciona un PC de una red privada local");
        return InetAddress.getByAddress(bytes);
    }

    private static JSONObject readHandshake(Socket s) throws Exception {
        DataInputStream input=new DataInputStream(s.getInputStream());
        byte[] tag=new byte[3];input.readFully(tag);
        if(!Arrays.equals(tag,new byte[]{'S','I','N'})) throw new IOException("El equipo no anuncia Remote Mouse");
        byte[] sizeBytes=new byte[3];input.readFully(sizeBytes);
        String length=new String(sizeBytes,StandardCharsets.US_ASCII).trim();
        if(!length.matches("[0-9]{1,3}")) throw new IOException("Saludo del servidor inválido");
        int size=Integer.parseInt(length);
        if(size<1 || size>512) throw new IOException("Saludo del servidor fuera de rango");
        byte[] payload=new byte[size]; input.readFully(payload);
        String[] fields=new String(payload,StandardCharsets.UTF_8).trim().split(" +");
        if(fields.length<4 || !"win".equals(fields[0]) || !fields[3].matches("[0-9]{1,6}"))
            throw new IOException("El servidor no es Windows o su versión no es compatible");
        int version=Integer.parseInt(fields[3]);
        if(version<530) throw new IOException("Esta prueba requiere servidor Windows 5.30 o posterior");
        return new JSONObject().put("type","win").put("auth",fields[1]).put("version",version);
    }

    void connect(Network network,String host) throws Exception {
        InetAddress address=privateAddress(host);
        close();
        Socket candidate=new Socket();socket=candidate;
        DatagramSocket udpCandidate=null;
        try {
            network.bindSocket(candidate);
            candidate.setTcpNoDelay(true);candidate.setSoTimeout(3000);
            candidate.connect(new InetSocketAddress(address,PORT),3000);
            JSONObject handshake=readHandshake(candidate);
            if(!"nop".equals(handshake.getString("auth")))
                throw new IOException("El servidor pide su contraseña. Esta APK de prueba todavía no admite autenticación del servidor.");
            if(socket!=candidate) throw new IOException("Conexión cancelada");
            udpCandidate=new DatagramSocket(null);
            commandSocket=udpCandidate;
            network.bindSocket(udpCandidate);
            udpCandidate.bind(new InetSocketAddress(0));
            udpCandidate.connect(address,PORT);
            if(socket!=candidate) throw new IOException("Conexión cancelada");
            version=handshake.getInt("version"); ready=true;candidate.setSoTimeout(0);
            Thread monitor=new Thread(() -> {
                try {byte[] buffer=new byte[512]; while(socket==candidate && candidate.getInputStream().read(buffer)!=-1) { }}
                catch(IOException ignored) { }
                finally {if(socket==candidate) close();}
            },"Mando-server-monitor");
            monitor.setDaemon(true);monitor.start();
        } catch(Exception error) {
            if(udpCandidate!=null)udpCandidate.close();
            if(commandSocket==udpCandidate)commandSocket=null;
            if(socket==candidate) close(); else try{candidate.close();}catch(IOException ignored){ }
            throw error;
        }
    }

    private void sendCommand(byte[]... frames) throws IOException {
        DatagramSocket current=commandSocket;
        if(!isConnected() || current==null || current.isClosed())throw new IOException("Vuelve a conectar con el PC");
        try {
            // Official server's legacy UDP path accepts one complete UTF-8 frame per datagram.
            // It applies the same authentication gate; this client only enables it after nop.
            for(byte[] frame:frames)current.send(new DatagramPacket(frame,frame.length));
        } catch(IOException error) {
            close();throw new IOException("No se pudo enviar el comando al PC. La acción no se repetirá.");
        }
    }

    private int number(JSONObject data,String key,int low,int high) throws Exception {
        Object value=data.get(key);
        if(!(value instanceof Integer) && !(value instanceof Long)) throw new IOException("Valor numérico inválido");
        long n=((Number)value).longValue();
        if(n<low || n>high) throw new IOException("Valor fuera de rango");
        return (int)n;
    }

    void action(JSONObject data) throws Exception {
        String type=data.getString("type");
        if("move".equals(type)) {
            sendCommand(Protocol.move(number(data,"dx",-500,500),number(data,"dy",-500,500)));
        } else if("click".equals(type)) {
            String button=data.getString("button");
            String code="left".equals(button)?"l":"right".equals(button)?"r":null;
            if(code==null) throw new IOException("Botón no permitido");
            sendCommand(Protocol.frame("mos","R "+code+" d"),Protocol.frame("mos","R "+code+" u"));
        } else if("scroll".equals(type)) {
            int steps=number(data,"steps",-10,10);
            for(int i=0;i<Math.abs(steps);i++) sendCommand(Protocol.frame("mos",steps>0?"w 0":"w 1"));
        } else if("text".equals(type)) {
            String text=data.getString("text");
            if(text.isEmpty() || text.length()>500) throw new IOException("Escribe entre 1 y 500 caracteres");
            // Keep each [noe] frame under the 3-digit byte-length limit, on codepoint boundaries.
            StringBuilder chunk=new StringBuilder();int bytes=0;
            for(int offset=0;offset<text.length();) {
                int point=text.codePointAt(offset);offset+=Character.charCount(point);
                if(point>=0xD800 && point<=0xDFFF) throw new IOException("Texto Unicode inválido");
                String item=new String(Character.toChars(point));int size=item.getBytes(StandardCharsets.UTF_8).length;
                if(bytes+size>900) {sendCommand(Protocol.text(chunk.toString()));chunk.setLength(0);bytes=0;}
                chunk.append(item);bytes+=size;
            }
            if(chunk.length()>0) sendCommand(Protocol.text(chunk.toString()));
        } else if("key".equals(type)) {
            String key=data.getString("key");
            Map<String,String> media=new HashMap<>();
            media.put("VOLUME_UP","volume_up");media.put("VOLUME_DOWN","volume_down");media.put("PREVIOUS","media_prev_track");
            media.put("NEXT","media_next_track");media.put("PLAY_PAUSE","media_play_pause");media.put("MUTE","volume_mute");
            JSONArray mods=data.optJSONArray("modifiers");
            if(media.containsKey(key)) {sendCommand(Protocol.systemMedia(media.get(key)));return;}
            String name=KEYS.get(key);
            if(name==null) throw new IOException("Tecla no permitida");
            if(mods!=null && mods.length()>0) {
                sendCombination(name,mods);
            } else if(Arrays.asList("SPACE","LEFT","RIGHT","UP","DOWN").contains(key)) {
                sendCommand(Protocol.navigationKey(name.toLowerCase(java.util.Locale.US)));
            } else sendCommand(Protocol.frame("key",name));
        } else throw new IOException("Acción no permitida");
    }

    private void sendCombination(String key,JSONArray mods) throws Exception {
        if(mods.length()>4) throw new IOException("Demasiados modificadores");
        Map<String,String> names=new HashMap<>();
        names.put("CTRL","ctrl");names.put("ALT","alt");names.put("SHIFT","shift");names.put("WIN","cmd");
        Set<String> selected=new HashSet<>();
        StringBuilder payload=new StringBuilder("[kld]").append(key);
        for(int i=0;i<mods.length();i++) {
            String modifier=mods.getString(i);
            if(!names.containsKey(modifier) || !selected.add(modifier)) throw new IOException("Modificador inválido");
            payload.append("[*]").append(names.get(modifier));
        }
        // Mirrors the observed one-shot combination, without inventing release packets.
        sendCommand(Protocol.frame("key",payload.toString()));
    }

    JSONArray scan(Network network,LinkProperties properties) throws Exception {
        if(properties==null) throw new IOException("No se puede leer la red Wi-Fi");
        try(Socket test=new Socket()) {network.bindSocket(test);}
        LinkAddress selected=null;
        for(LinkAddress link:properties.getLinkAddresses()) if(link.getAddress() instanceof Inet4Address) {selected=link;break;}
        if(selected==null) throw new IOException("La red Wi-Fi no tiene IPv4");
        byte[] local=selected.getAddress().getAddress();
        int prefix=Math.max(24,selected.getPrefixLength());
        if(prefix>30) throw new IOException("Red punto a punto: introduce la IP manualmente");
        int localInt=((local[0]&255)<<24)|((local[1]&255)<<16)|((local[2]&255)<<8)|(local[3]&255);
        int mask=-1<<(32-prefix), base=localInt&mask, count=1<<(32-prefix);
        final List<JSONObject> found=Collections.synchronizedList(new ArrayList<JSONObject>());
        ExecutorService probes=Executors.newFixedThreadPool(16);
        List<Future<?>> futures=new ArrayList<>();
        try {
            for(int host=1;host<count-1;host++) {
                int value=base+host;if(value==localInt)continue;
                final byte[] ip={(byte)(value>>>24),(byte)(value>>>16),(byte)(value>>>8),(byte)value};
                InetAddress address=InetAddress.getByAddress(ip);
                try {privateAddress(address.getHostAddress());}catch(IOException skip){continue;}
                futures.add(probes.submit(() -> {
                    try(Socket s=new Socket()) {
                        network.bindSocket(s);s.setSoTimeout(600);
                        s.connect(new InetSocketAddress(address,PORT),350);
                        JSONObject greeting=readHandshake(s);
                        found.add(new JSONObject().put("host",address.getHostAddress()).put("version",greeting.getInt("version"))
                                .put("requiresPassword",!"nop".equals(greeting.getString("auth"))));
                    }catch(Exception ignored){ }
                }));
            }
            for(Future<?> probe:futures) probe.get(3,TimeUnit.SECONDS);
        } finally {probes.shutdownNow();}
        return new JSONArray(found);
    }
}
