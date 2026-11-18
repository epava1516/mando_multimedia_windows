package net.mando.personal;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Opt-in, bounded diagnostic export. Never receives actions, credentials or exception text. */
final class AdbDiagnostics {
    private static Context context;
    private static boolean enabled;
    private static final ArrayDeque<String> events=new ArrayDeque<>();
    private static final Set<String> CODES=new HashSet<>(Arrays.asList("ENABLED","CONNECTED","DISCONNECTED","ACTION_REJECTED","REQUEST_FAILED","CAPTURE_FAILED","CAPTURE_OK","APP_OPENED"));
    private static String report="Sin informe del servidor. Los eventos locales no sustituyen el sondeo del PC.";
    static synchronized void init(Context value) {
        if(context!=null)return;
        context=value.getApplicationContext();
        enabled=context.getSharedPreferences("adb_diagnostics",Context.MODE_PRIVATE).getBoolean("enabled",false);
        if(enabled)report=context.getSharedPreferences("adb_diagnostics",Context.MODE_PRIVATE).getString("report",report);
    }
    static synchronized boolean enabled(){return enabled;}
    private static File target() {
        File root=context==null?null:context.getExternalFilesDir(null);
        return root==null?null:new File(root,"diagnostics/latest.txt");
    }
    static synchronized String path(){File file=target();return file==null?"Almacenamiento no disponible":file.getAbsolutePath();}
    static synchronized void setEnabled(boolean value) {
        if(context==null)return;
        enabled=value;context.getSharedPreferences("adb_diagnostics",Context.MODE_PRIVATE).edit().putBoolean("enabled",value).remove("report").apply();
        events.clear();report="Sin informe del servidor.";
        File file=target();if(file!=null)new AtomicFile(file).delete();
        if(value)event("ENABLED");
    }
    static synchronized void event(String code) {
        if(!enabled||!CODES.contains(code))return;
        while(events.size()>=32)events.removeFirst();
        events.addLast(System.currentTimeMillis()+" "+code);
        Log.i("MandoDiag",code); // Only fixed codes in logcat; report stays in the opt-in file.
        save();
    }
    static synchronized void server(JSONObject data) throws Exception {
        if(!enabled)return;
        // Reuse the same field-filtered formatter as the visible diagnostic UI.
        String safe=DiagnosticText.format(data);
        report="Capturado: "+System.currentTimeMillis()+" epoch ms\n"+(safe.length()>32768?safe.substring(0,32768):safe);
        context.getSharedPreferences("adb_diagnostics",Context.MODE_PRIVATE).edit().putString("report",report).apply();
        event("CAPTURE_OK");
    }
    private static void save() {
        File file=target();if(file==null)return;
        File directory=file.getParentFile();if(!directory.isDirectory()&&!directory.mkdirs())return;
        StringBuilder text=new StringBuilder("Mando · diagnóstico ADB (activado por el usuario)\nEventos locales: epoch ms; máximo 32\n");
        for(String event:events)text.append(event).append('\n');
        text.append("\nÚLTIMO INFORME DEL SERVIDOR (puede ser anterior a la desconexión):\n").append(report);
        AtomicFile atomic=new AtomicFile(file);FileOutputStream output=null;
        try {output=atomic.startWrite();output.write(text.toString().getBytes(StandardCharsets.UTF_8));atomic.finishWrite(output);}
        catch(Exception ignored){if(output!=null)atomic.failWrite(output);Log.i("MandoDiag","EXPORT_FAILED");}
    }
}
