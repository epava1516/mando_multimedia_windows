package net.mando.personal;
import org.json.JSONObject;
/** JVM-only sink. The real Android exporter is tested separately through ADB. */
final class AdbDiagnostics {
    static boolean active;static int captures;
    static boolean enabled(){return active;}
    static void event(String code){}
    static void server(JSONObject data){captures++;}
}
