package net.mando.personal;
import org.json.JSONArray;
import org.json.JSONObject;
/** Whitelist fields: never copy arbitrary server JSON, exception text or credentials. */
final class DiagnosticText {
    private static String code(String value) {return value.matches("[A-Z_]{1,48}")?value:"UNKNOWN";}
    private static String match(JSONObject data,String key) {
        return data.isNull(key)||!data.has(key)?"sin confirmar":data.optBoolean(key)?"sí":"no";
    }
    private static void probe(StringBuilder text,String title,JSONObject data) throws Exception {
        if(data==null)return;
        text.append("\n\n").append(title).append(": ").append(code(data.optString("kind","UNKNOWN")))
            .append("\nEtapa: ").append(code(data.optString("stage","UNKNOWN")))
            .append("\nWin32: ").append(Math.max(0,data.optInt("win32",0)))
            .append(" · ").append(Math.max(0,data.optInt("elapsed_ms",0))).append(" ms")
            .append("\nBytes enviados / recibidos: ").append(Math.max(0,data.optInt("written",0)))
            .append(" / ").append(Math.max(0,data.optInt("read",0)))
            .append("\nPID del canal: ").append(Math.max(0,data.optInt("peer_pid",0)))
            .append(" · coincide: ").append(match(data,"pid_matches"));
        JSONArray trace=data.optJSONArray("trace");
        if(trace!=null)for(int i=0;i<Math.min(16,trace.length());i++) {
            JSONObject step=trace.getJSONObject(i);
            text.append("\n  ").append(code(step.optString("stage","UNKNOWN")))
                .append(" +").append(Math.max(0,step.optInt("at_ms",0))).append(" ms");
        }
    }
    static String format(JSONObject data) throws Exception {
        String version=data.optString("server_version","");
        if(!version.matches("[0-9A-Za-z.\\-]{1,40}"))version="desconocida";
        StringBuilder text=new StringBuilder("Mando Server ").append(version)
            .append("\nTiempo activo: ").append(Math.max(0,data.optLong("uptime_s",0))).append(" s")
            .append("\nClientes: ").append(Math.max(0,Math.min(4,data.optInt("clients",0))));
        JSONObject desktop=data.getJSONObject("desktop");
        text.append("\n\nServicio de escritorio: ").append(desktop.optBoolean("service_reachable")?"accesible":"no disponible")
            .append("\nSondeo: ").append(code(desktop.optString("probe","UNKNOWN")))
            .append("\nProceso de bloqueo: ").append(desktop.optBoolean("worker_running")?"en ejecución":"sin confirmar")
            .append("\nCanal del proceso: ").append(desktop.optBoolean("worker_verified")?"verificado":"sin confirmar");
        if(desktop.has("locked"))text.append("\nSesión: ").append(desktop.optBoolean("locked")?"bloqueada":"desbloqueada");
        text.append("\nÚltima etapa de fallo: ").append(code(desktop.optString("last_stage","NONE")))
            .append("\nCódigo Windows: ").append(Math.max(0,desktop.optInt("last_win32",0)))
            .append("\n\nEventos recientes (UTC, desde el arranque):\n");
        JSONArray events=data.optJSONArray("events");
        if(events!=null)for(int i=0;i<Math.min(24,events.length());i++) {
            JSONObject event=events.getJSONObject(i);String time=event.optString("time","");
            if(!time.matches("[0-9T:Z\\-]{20}"))time="hora desconocida";
            text.append(time).append(" · ").append(code(event.optString("code","UNKNOWN"))).append('\n');
        }
        JSONObject advanced=data.optJSONObject("advanced");
        if(advanced!=null) {
            text.append("\nComprobaciones avanzadas (solo lectura):");
            JSONObject local=advanced.optJSONObject("local");
            if(local!=null) {
                text.append("\nServicio: ").append(code(local.optString("service_state","UNKNOWN")))
                    .append(" · PID ").append(Math.max(0,local.optInt("service_pid",0)))
                    .append("\nCuenta del servicio: ").append(code(local.optString("service_account","UNKNOWN")))
                    .append("\nWin32 SCM: ").append(Math.max(0,local.optInt("service_error",0)))
                    .append("\nConfiguración de cuenta: ").append(code(local.optString("allowed_sid_file","UNKNOWN")))
                    .append("\nCuenta autorizada coincide: ").append(match(local,"account_match"))
                    .append("\nSesión coincide con consola: ").append(match(local,"session_match"))
                    .append("\nSesión cliente / consola: ").append(local.isNull("client_session")?"?":local.optInt("client_session",0))
                    .append(" / ").append(local.isNull("console_session")?"?":local.optInt("console_session",0))
                    .append("\nWin32 identidad: ").append(Math.max(0,local.optInt("identity_error",0)));
            }
            probe(text,"Sondeo anterior",advanced.optJSONObject("previous"));
            probe(text,"Sondeo nuevo (máximo 2 s)",advanced.optJSONObject("pipe"));
            text.append("\n\nTIMEOUT = espera agotada; EOF = canal cerrado; WIN32 = error nativo.\nNo se ejecutan comandos, no se cambia el servicio ni se inyecta entrada.\nEl sondeo nuevo reconecta el canal local, sin repetir acciones.");
        }
        text.append("\nSin textos, contraseñas, portapapeles ni tokens.\nServicio accesible no garantiza entrada en bloqueo.\nCódigo 0: no se capturó un error Win32.");
        return text.toString();
    }
}
