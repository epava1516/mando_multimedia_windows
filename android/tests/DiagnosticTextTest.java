package net.mando.personal;
import org.json.JSONObject;
import org.json.JSONArray;
/** Run with an org.json JVM implementation, not android.jar stub methods. */
public final class DiagnosticTextTest {
    public static void main(String[] args) throws Exception {
        JSONArray events=new JSONArray();
        for(int i=0;i<100;i++)events.put(new JSONObject().put("time","2026-11-18T00:00:00Z").put("code","INPUT_REJECTED").put("text","PRIVATE_SECRET"));
        JSONObject desktop=new JSONObject().put("service_reachable",true).put("probe","OK")
            .put("last_stage","<PRIVATE_SECRET>").put("last_win32",5).put("token","PRIVATE_SECRET");
        JSONObject data=new JSONObject().put("server_version","0.5.0-alpha.13").put("uptime_s",10)
            .put("clients",2).put("desktop",desktop).put("events",events).put("clipboard","PRIVATE_SECRET");
        String report=DiagnosticText.format(data);
        if(report.contains("PRIVATE_SECRET")||report.contains("<"))throw new AssertionError("Unapproved fields escaped into report");
        if(!report.contains("Código Windows: 5"))throw new AssertionError("Missing diagnostic code");
        if(report.split("INPUT_REJECTED",-1).length-1!=24)throw new AssertionError("Unbounded report");
        if(report.contains("Canal del proceso: verificado"))throw new AssertionError("Overstated readiness");
        System.out.println("Diagnostic report: field whitelist, bounded events and truthful readiness passed");
    }
}
