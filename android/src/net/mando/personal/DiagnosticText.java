package net.mando.personal;
import org.json.JSONArray;
import org.json.JSONObject;
/** Whitelist fields: never copy arbitrary server JSON, exception text or credentials. */
final class DiagnosticText {
    private static String code(String value) {return value.matches("[A-Z_]{1,48}")?value:"UNKNOWN";}
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
        text.append("\nSin textos, contraseñas, portapapeles ni tokens.\nServicio accesible no garantiza entrada en bloqueo.\nCódigo 0: no se capturó un error Win32.");
        return text.toString();
    }
}
