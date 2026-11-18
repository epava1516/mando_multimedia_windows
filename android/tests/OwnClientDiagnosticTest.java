package net.mando.personal;
import org.json.JSONObject;
import java.io.*;
import java.lang.reflect.Field;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Framed protocol regression: rejected input is never repeated or replaced by input. */
public final class OwnClientDiagnosticTest {
    static void field(OwnClient client,String key,Object value)throws Exception {
        Field f=OwnClient.class.getDeclaredField(key);f.setAccessible(true);f.set(client,value);
    }
    static void frame(DataOutputStream output,JSONObject data)throws Exception {
        byte[] body=data.toString().getBytes(StandardCharsets.UTF_8);output.writeInt(body.length);output.write(body);
    }
    static JSONObject read(DataInputStream input)throws Exception {
        byte[] body=new byte[input.readInt()];input.readFully(body);return new JSONObject(new String(body,StandardCharsets.UTF_8));
    }
    static void scenario(boolean enabled,boolean validSequence)throws Exception {
        AdbDiagnostics.active=enabled;AdbDiagnostics.captures=0;
        ByteArrayOutputStream replies=new ByteArrayOutputStream();DataOutputStream output=new DataOutputStream(replies);
        frame(output,new JSONObject().put("id",validSequence?1:999).put("error","Windows no ha aceptado la accion; no se repetira"));
        frame(output,new JSONObject().put("id",2).put("ok",true).put("diagnostics",new JSONObject()));
        frame(output,new JSONObject().put("id",3).put("ok",true));
        OwnClient client=new OwnClient();ByteArrayOutputStream sent=new ByteArrayOutputStream();
        field(client,"ready",true);field(client,"socket",new Socket());
        field(client,"input",new DataInputStream(new ByteArrayInputStream(replies.toByteArray())));
        field(client,"output",new DataOutputStream(sent));
        field(client,"capabilities",new JSONObject().put("diagnostics_v1",true).put("diagnostics_advanced_v1",true));
        try{client.action(new JSONObject().put("type","click").put("button","left"));throw new AssertionError("Rejection ignored");}
        catch(IOException expected){
            if ((expected instanceof OwnClient.ActionRejected) != validSequence) throw expected;
        }
        if(client.isConnected()!=validSequence)throw new AssertionError("Wrong session state after rejection");
        DataInputStream requests=new DataInputStream(new ByteArrayInputStream(sent.toByteArray()));
        if(!read(requests).getString("op").equals("action"))throw new AssertionError();
        if(enabled&&validSequence) {
            JSONObject diagnostic=read(requests);
            if(!diagnostic.getString("op").equals("diagnostics_advanced")||diagnostic.has("action")||diagnostic.getInt("id")!=2)throw new AssertionError("Unsafe followup");
            if(AdbDiagnostics.captures!=1)throw new AssertionError("Missing failure report");
        } else if(AdbDiagnostics.captures!=0)throw new AssertionError("Captured without opt-in or after invalid response");
        if(requests.available()!=0)throw new AssertionError("Input replayed");
        if(validSequence) {
            if(!enabled) {
                ByteArrayOutputStream next = new ByteArrayOutputStream();
                frame(new DataOutputStream(next),new JSONObject().put("id",2).put("ok",true));
                field(client,"input",new DataInputStream(new ByteArrayInputStream(next.toByteArray())));
            }
            client.action(new JSONObject().put("type","move").put("dx",1).put("dy",0));
            DataInputStream recovery = new DataInputStream(new ByteArrayInputStream(sent.toByteArray()));
            JSONObject last = null;
            while(recovery.available()>0) last=read(recovery);
            if(!"move".equals(last.getJSONObject("action").getString("type")) ||
                    last.getInt("id")!=(enabled?3:2)) throw new AssertionError("New input failed after rejection");
        }
    }
    public static void main(String[] args)throws Exception {
        scenario(true,true);scenario(false,true);scenario(true,false);
        System.out.println("OwnClient failure diagnostics: one read-only query, opt-in, sequence guard, no replay passed");
    }
}
