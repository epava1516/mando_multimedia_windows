package net.mando.personal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.graphics.Insets;
import android.view.WindowInsets;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.Toast;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** UI assets are bundled; server bytes are never executed or injected into the page. */
public final class MainActivity extends Activity {
    private static final String LOCAL = "https://mando.local/";
    private WebView web;
    private RemoteKeyboard editor;
    private LinearLayout keyboardRow;
    private final RemoteClient officialClient = new RemoteClient();
    private final OwnClient ownClient = new OwnClient();
    private volatile InputClient client = officialClient;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(32));
    private volatile boolean destroyed;
    private volatile boolean foreground;
    private InfraredController infrared;
    private DeviceStore deviceStore;
    private final ResumeWindow resume = new ResumeWindow();
    private final Object resumeStoreLock=new Object();
    private volatile boolean resuming;
    private volatile boolean imeVisible;
    private boolean zoomSetupShowing;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        infrared = new InfraredController(this);
        deviceStore = new DeviceStore(this);
        restoreResumeCheckpoint();
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff102323);
        web = new WebView(this);
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        keyboardRow=new LinearLayout(this);
        keyboardRow.setPadding(12,8,12,8);
        keyboardRow.setBackgroundColor(0xff193030);
        editor=new RemoteKeyboard(this,new RemoteKeyboard.Sink() {
            public void text(String value) {
                try {sendKeyboard(new JSONObject().put("type","text").put("text",value));}catch(Exception ignored){ }
            }
            public void key(String value) {
                try {sendKeyboard(new JSONObject().put("type","key").put("key",value));}catch(Exception ignored){ }
            }
        });
        keyboardRow.addView(editor,new LinearLayout.LayoutParams(0,-2,1));
        Button closeKeyboard=new Button(this);
        closeKeyboard.setText("Cerrar");
        closeKeyboard.setOnClickListener(view -> showKeyboard(false));
        keyboardRow.addView(closeKeyboard,new LinearLayout.LayoutParams(-2,-2));
        keyboardRow.setVisibility(View.GONE);
        root.addView(keyboardRow,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                boolean visible=insets.isVisible(WindowInsets.Type.ime());
                if(imeVisible!=visible) {
                    imeVisible=visible;
                    web.post(() -> {
                        if(!destroyed)web.evaluateJavascript("window.mandoImeChanged && window.mandoImeChanged("+visible+")",null);
                    });
                }
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setDomStorageEnabled(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        web.setBackgroundColor(0xff102323);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view,String url) {
                if(foreground&&resuming)web.evaluateJavascript("window.mandoResuming && window.mandoResuming()",null);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true; // No page navigation, external hosts, or executable target content.
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String path = uri.getPath();
                if (!"https".equals(uri.getScheme()) || !"mando.local".equals(uri.getHost()) ||
                        uri.getPort() != -1 || !"GET".equals(request.getMethod())) return blocked();
                String file = "/".equals(path) ? "index.html" : "/app.js".equals(path) ? "app.js" :
                        "/style.css".equals(path) ? "style.css" : null;
                if (file == null) return blocked();
                try {
                    Map<String,String> headers = new HashMap<>();
                    headers.put("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'none'; frame-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'");
                    headers.put("X-Content-Type-Options", "nosniff");
                    headers.put("Cache-Control", "no-store");
                    String type = file.endsWith("js") ? "text/javascript" : file.endsWith("css") ? "text/css" : "text/html";
                    return new WebResourceResponse(type, "UTF-8", 200, "OK", headers, getAssets().open(file));
                } catch (IOException e) { return blocked(); }
            }
        });
        web.addJavascriptInterface(new Bridge(), "MandoNative");
        web.loadUrl(LOCAL);
    }

    private WebResourceResponse blocked() {
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", new HashMap<String,String>(), new ByteArrayInputStream(new byte[0]));
    }

    private void showKeyboard(boolean visible) {
        InputMethodManager manager=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if(visible && client.isConnected()) {
            keyboardRow.setVisibility(View.VISIBLE);
            editor.requestFocus();
            editor.post(() -> manager.showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT));
        } else {
            editor.finishAndSend();
            manager.hideSoftInputFromWindow(editor.getWindowToken(),0);
            keyboardRow.setVisibility(View.GONE);web.requestFocus();
        }
    }

    private void sendKeyboard(final JSONObject action) {
        if(destroyed || !foreground || resuming || !client.isConnected())return;
        final long stamp=resume.generation();
        try {
            worker.execute(() -> {
                if(!foreground||!resume.current(stamp))return;
                try {client.action(action);}
                catch(Exception error) {
                    runOnUiThread(() -> {
                        if(!destroyed && resume.current(stamp)) {
                            showKeyboard(false);
                            web.evaluateJavascript("window.mandoKeyboardError("+JSONObject.quote(safeMessage(error))+")",null);
                        }
                    });
                }
            });
        } catch(java.util.concurrent.RejectedExecutionException busy) {
            web.evaluateJavascript("window.mandoKeyboardError('Hay demasiadas teclas pendientes. Espera un momento.')",null);
        }
    }

    private void showLockSetup() {
        if(destroyed || isFinishing())return;
        new AlertDialog.Builder(this).setTitle("Bloquear el PC")
            .setMessage("El servidor no ha permitido Win+L. Instala una vez el ajuste MandoLock en Windows y comprueba Ctrl+Alt+L allí. Después confirma aquí que está instalado.")
            .setPositiveButton("Ya está instalado",(dialog,which) -> {
                getPreferences(MODE_PRIVATE).edit().putBoolean("lockHelperInstalled",true).apply();
                web.evaluateJavascript("window.mandoLockChanged && window.mandoLockChanged(true)",null);
                Toast.makeText(this,"Configurado. Pulsa otra vez el candado para probar.",Toast.LENGTH_LONG).show();
            })
            .setNeutralButton("Desactivar",(dialog,which) -> {
                getPreferences(MODE_PRIVATE).edit().putBoolean("lockHelperInstalled",false).apply();
                web.evaluateJavascript("window.mandoLockChanged && window.mandoLockChanged(false)",null);
            }).setNegativeButton("Cancelar",null).show();
    }

    private void showZoomSetup() {
        if(destroyed || isFinishing() || zoomSetupShowing)return;
        if(client==ownClient) {
            new AlertDialog.Builder(this).setTitle("Zoom integrado")
                .setMessage("El paquete completo de Windows incluye el zoom visual. Coloca el cursor sobre Firefox y pellizca en Control. Si falta el componente, instala Instalar-MandoCompleto.ps1 y vuelve a conectar.")
                .setPositiveButton("Cerrar",null).show();return;
        }
        zoomSetupShowing = true;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Zoom visual de Firefox")
            .setMessage("Instala MandoTouchZoom una vez en Windows. Comprueba Ctrl+Alt+F12 para ampliar y Ctrl+Alt+F11 para reducir con Firefox activo y el cursor sobre la página. Después confirma aquí. El pellizco enviará esos gestos táctiles alrededor del cursor.")
            .setPositiveButton("Ya está instalado",(prompt,which) -> {
                getPreferences(MODE_PRIVATE).edit().putBoolean("zoomHelperInstalled",true).apply();
                web.evaluateJavascript("window.mandoZoomChanged && window.mandoZoomChanged(true)",null);
            })
            .setNeutralButton("Desactivar",(prompt,which) -> {
                getPreferences(MODE_PRIVATE).edit().putBoolean("zoomHelperInstalled",false).apply();
                web.evaluateJavascript("window.mandoZoomChanged && window.mandoZoomChanged(false)",null);
            }).setNegativeButton("Cancelar",null).create();
        dialog.setOnDismissListener(prompt -> zoomSetupShowing = false);
        dialog.show();
    }

    private Network wifi() throws IOException {
        ConnectivityManager manager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        for (Network network : manager.getAllNetworks()) {
            NetworkCapabilities caps = manager.getNetworkCapabilities(network);
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return network;
        }
        throw new IOException("No encuentro una red Wi-Fi disponible. Activa Wi-Fi y vuelve a conectar.");
    }

    private void reply(final String id, final JSONObject result) {
        runOnUiThread(() -> {
            if (!destroyed) web.evaluateJavascript("window.nativeReply(" + JSONObject.quote(id) + "," + result.toString() + ")", null);
        });
    }

    private final class Bridge {
        @JavascriptInterface public void request(final String id, final String raw) {
            if (id == null || !id.matches("[0-9]{1,9}") || raw == null || raw.length() > 8192 || destroyed) return;
            final long requestedAt = android.os.SystemClock.elapsedRealtime();
            final JSONObject request;
            final String requestPath;
            try {request=new JSONObject(raw);requestPath=request.getString("path");}
            catch(Exception invalid){try{reply(id,new JSONObject().put("error","Petición inválida"));}catch(Exception ignored){}return;}
            if("/api/logout".equals(requestPath)||"/api/pair".equals(requestPath)||
                    ("/api/own-forget".equals(requestPath)&&(client==ownClient||resume.owns("own"))))cancelResume();
            final long stamp=resume.generation();
            final boolean duringRecovery=resuming;
            try {
                worker.execute(() -> {
                    JSONObject result = new JSONObject();
                    try {
                        String path = requestPath;
                        JSONObject data = request.optJSONObject("data");
                        boolean input="/api/action".equals(path)||"/api/lock".equals(path)||"/api/keyboard".equals(path)||"/api/ir/send".equals(path);
                        if(input && (!foreground||!resume.current(stamp)||(duringRecovery&&!"/api/ir/send".equals(path))))result.put("expired",true);
                        else if ("/api/ir/status".equals(path)) {
                            result.put("available", infrared.isAvailable());
                            result.put("profile", "LGTV1");
                        } else if ("/api/ir/send".equals(path)) {
                            if (!foreground || android.os.SystemClock.elapsedRealtime() - requestedAt > 750)
                                throw new IOException("La señal IR ha caducado. Pulsa de nuevo.");
                            if (data == null) throw new IOException("Falta el botón infrarrojo");
                            infrared.send(data.getString("command"));
                            result.put("ok", true);
                        } else if ("/api/status".equals(path)) {
                            if(client==ownClient && ownClient.isConnected()) {
                                try {ownClient.refreshStatus();}catch(Exception ignored){ }
                            }
                            result=sessionState();
                            result.put("paired", client.isConnected());
                            result.put("dryRun", client==ownClient && ownClient.isDryRun());
                            result.put("imeVisible",imeVisible);
                        } else if ("/api/own-forget".equals(path)) {
                            ownClient.close();deviceStore.forget();result.put("ok",true);
                        } else if ("/api/zoom-setup".equals(path)) {
                            runOnUiThread(() -> showZoomSetup());result.put("ok",true);
                        } else if ("/api/lock-setup".equals(path)) {
                            if(client==ownClient)runOnUiThread(() -> Toast.makeText(MainActivity.this,"El servidor propio incluye el bloqueo directo.",Toast.LENGTH_LONG).show());
                            else runOnUiThread(() -> showLockSetup());result.put("ok",true);
                        } else if ("/api/lock".equals(path)) {
                            if(client==ownClient) {
                                client.action(new JSONObject().put("type","lock"));result.put("ok",true);
                            } else if(!getPreferences(MODE_PRIVATE).getBoolean("lockHelperInstalled",false)) {
                                runOnUiThread(() -> showLockSetup());result.put("needsSetup",true);
                            } else {
                                client.action(new JSONObject().put("type","key").put("key","L")
                                    .put("modifiers",new JSONArray().put("CTRL").put("ALT")));
                                result.put("ok",true);
                            }
                        } else if ("/api/settings".equals(path)) {
                            if(data!=null && data.has("sensitivity")) {
                                Object rawValue=data.get("sensitivity");
                                if(!(rawValue instanceof Number))throw new IOException("Sensibilidad inválida");
                                double value=((Number)rawValue).doubleValue();
                                if(!Double.isFinite(value) || value<0.15 || value>2.5)throw new IOException("Sensibilidad fuera de rango");
                                getPreferences(MODE_PRIVATE).edit().putFloat("sensitivity",(float)value).apply();
                            }
                            if(data!=null && data.has("irTabVisible")) {
                                Object value=data.get("irTabVisible");
                                if(!(value instanceof Boolean))throw new IOException("Preferencia IR inválida");
                                getPreferences(MODE_PRIVATE).edit().putBoolean("irTabVisible",(Boolean)value).apply();
                            }
                            result.put("ownDeviceSaved",deviceStore.hasSaved());
                            result.put("irTabVisible",getPreferences(MODE_PRIVATE).getBoolean("irTabVisible",infrared.isAvailable()));
                            result.put("sensitivity",getPreferences(MODE_PRIVATE).getFloat("sensitivity",0.65f));
                            result.put("lockHelperInstalled",getPreferences(MODE_PRIVATE).getBoolean("lockHelperInstalled",false) || client==ownClient);
                            result.put("zoomHelperInstalled",client==ownClient ? ownClient.supportsZoom() : getPreferences(MODE_PRIVATE).getBoolean("zoomHelperInstalled",false));
                        } else if ("/api/keyboard".equals(path)) {
                            final boolean visible=data!=null && data.optBoolean("visible",false);
                            if(visible && !client.isConnected())throw new IOException("Conecta primero con el PC");
                            runOnUiThread(() -> showKeyboard(visible));result.put("ok",true);
                        } else if ("/api/pair".equals(path)) {
                            if (data == null) throw new IOException("Falta la dirección del PC");
                            if(!foreground||!resume.current(stamp))throw new IOException("Abre Mando para conectar");
                            officialClient.close();ownClient.close();
                            String mode=data.optString("mode","official");
                            if(!"own".equals(mode) && !"official".equals(mode))throw new IOException("Tipo de servidor inválido");
                            boolean own="own".equals(mode);
                            Network network=wifi();
                            if(own) {
                                try {
                                    String code=data.optString("code","");
                                    JSONObject profile=ownClient.connect(network,code,code.isEmpty()?deviceStore.load():null);
                                    deviceStore.save(profile);
                                }catch(Exception error){ownClient.close();throw error;}
                            } else officialClient.connect(network,data.getString("host"));
                            if(!resume.connected(stamp,new ResumeWindow.Target(mode,own?"":data.getString("host")))) {
                                officialClient.close();ownClient.close();throw new IOException("Conexión cancelada");
                            }
                            client=own ? ownClient : officialClient;
                            result.put("ok", true).put("mode",own ? "own" : "official");
                            result.put("version", own ? "Mando v1 TLS" : String.valueOf(officialClient.serverVersion()));
                            result.put("directLock",own).put("dryRun",own && ownClient.isDryRun());
                            result.put("secureDesktop",own && ownClient.supportsSecureDesktop());
                            result.put("lockHelperInstalled",own || getPreferences(MODE_PRIVATE).getBoolean("lockHelperInstalled",false));
                            result.put("zoomHelperInstalled",own ? ownClient.supportsZoom() : getPreferences(MODE_PRIVATE).getBoolean("zoomHelperInstalled",false));
                        } else if ("/api/scan".equals(path)) {
                            ConnectivityManager manager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
                            Network network=wifi();
                            result.put("servers",officialClient.scan(network,manager.getLinkProperties(network)));
                        } else if ("/api/logout".equals(path)) {
                            client.close(); result.put("ok", true);
                        } else if ("/api/action".equals(path)) {
                            if (data == null) throw new IOException("Falta la acción");
                            if ("zoom".equals(data.optString("type")) &&
                                    !(client==ownClient ? ownClient.supportsZoom() : getPreferences(MODE_PRIVATE).getBoolean("zoomHelperInstalled",false))) {
                                runOnUiThread(() -> showZoomSetup());result.put("needsSetup",true);
                            } else {
                                if (("zoom".equals(data.optString("type")) || "task_view".equals(data.optString("type"))) &&
                                        (!foreground || android.os.SystemClock.elapsedRealtime()-requestedAt>750))
                                    result.put("expired",true);
                                else {client.action(data); result.put("ok", true);}
                            }
                        } else throw new IOException("Acción no permitida");
                    } catch (Exception e) {
                        try {
                            if(!resume.current(stamp) && ("/api/action".equals(requestPath)||"/api/lock".equals(requestPath)||"/api/keyboard".equals(requestPath)||"/api/ir/send".equals(requestPath)))result.put("expired",true);
                            else result.put("error",safeMessage(e));
                        } catch(Exception ignored){ }
                    }
                    reply(id, result);
                });
            } catch (java.util.concurrent.RejectedExecutionException busy) {
                try { reply(id, new JSONObject().put("error", "Demasiadas acciones pendientes. Espera un momento.")); }
                catch (Exception ignored) { }
            }
        }
    }

    private int bootCount() {
        try{return android.provider.Settings.Global.getInt(getContentResolver(),android.provider.Settings.Global.BOOT_COUNT,-1);}
        catch(Exception ignored){return -1;}
    }
    private void restoreResumeCheckpoint() {
        android.content.SharedPreferences prefs=getPreferences(MODE_PRIVATE);
        int boot=bootCount();
        if(boot<0 || prefs.getInt("resumeBoot",-2)!=boot)return;
        String mode=prefs.getString("resumeMode","");
        long paused=prefs.getLong("resumePaused",-1),now=android.os.SystemClock.elapsedRealtime();
        if(paused<0 || now<paused || now-paused>=ResumeWindow.TTL_MS)return;
        if(!"own".equals(mode)&&!"official".equals(mode))return;
        resume.restore(new ResumeWindow.Target(mode,prefs.getString("resumeHost","")),paused);
    }
    private void saveResumeCheckpoint(){saveResumeCheckpoint(false);}
    private void saveResumeCheckpoint(boolean immediate) {
        ResumeWindow.Snapshot checkpoint=resume.checkpoint();
        android.content.SharedPreferences.Editor prefs=getPreferences(MODE_PRIVATE).edit();
        if(checkpoint==null)prefs.remove("resumePaused").remove("resumeMode").remove("resumeHost").remove("resumeBoot");
        else prefs.putLong("resumePaused",checkpoint.pausedAt).putString("resumeMode",checkpoint.target.mode)
            .putString("resumeHost",checkpoint.target.host).putInt("resumeBoot",bootCount());
        if(immediate)prefs.commit();else prefs.apply();
    }
    private void cancelResume() {
        synchronized(resumeStoreLock){resume.clear();resuming=false;saveResumeCheckpoint(true);}
        officialClient.close();ownClient.close();
    }
    private JSONObject sessionState()throws Exception {
        return new JSONObject().put("paired",client.isConnected()).put("resuming",resuming)
            .put("mode",client==ownClient?"own":"official").put("dryRun",client==ownClient&&ownClient.isDryRun())
            .put("lockHelperInstalled",client==ownClient||getPreferences(MODE_PRIVATE).getBoolean("lockHelperInstalled",false))
            .put("zoomHelperInstalled",client==ownClient?ownClient.supportsZoom():getPreferences(MODE_PRIVATE).getBoolean("zoomHelperInstalled",false));
    }
    private void publishSession(JSONObject result){publishSession(result,resume.generation());}
    private void publishSession(JSONObject result,long stamp) {
        runOnUiThread(() -> {if(!destroyed&&foreground&&resume.current(stamp))web.evaluateJavascript("window.mandoSessionResult && window.mandoSessionResult("+result.toString()+")",null);});
    }
    private void recoverSession(ResumeWindow.Ticket ticket) {
        try {
            if(!resume.current(ticket.generation))return;
            Network network=wifi();
            boolean own="own".equals(ticket.target.mode);
            if(own) {
                JSONObject saved=deviceStore.load();
                if(saved==null)throw new IOException("Vuelve a emparejar el servidor propio");
                ownClient.connect(network,"",saved);
            } else officialClient.connect(network,ticket.target.host);
            if(!resume.connected(ticket.generation,ticket.target)){officialClient.close();ownClient.close();return;}
            client=own?ownClient:officialClient;resuming=false;
            publishSession(sessionState().put("recovered",true),ticket.generation);
        } catch(Exception error) {
            officialClient.close();ownClient.close();
            if(resume.current(ticket.generation)) {
                resuming=false;
                try {publishSession(sessionState().put("failed",true).put("error",safeMessage(error)),ticket.generation);}catch(Exception ignored){ }
            }
        }
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        if (message != null && message.contains("EPERM"))
            return "La VPN bloquea el acceso Wi-Fi de Mando. Excluye solo Mando del túnel y vuelve a conectar.";
        if (message == null || message.length() > 250) return "No se pudo completar la conexión o el comando.";
        return message;
    }

    @Override protected void onStart() {
        super.onStart();foreground=true;
        ResumeWindow.Ticket ticket=resume.start(android.os.SystemClock.elapsedRealtime());
        resuming=ticket!=null;
        if(ticket!=null) {
            web.evaluateJavascript("window.mandoResuming && window.mandoResuming()",null);
            try {worker.execute(() -> recoverSession(ticket));}
            catch(java.util.concurrent.RejectedExecutionException busy) {
                resuming=false;
                try{publishSession(sessionState().put("failed",true).put("error","Espera un momento y vuelve a conectar."));}catch(Exception ignored){ }
            }
        } else try{publishSession(sessionState());}catch(Exception ignored){ }
    }

    @Override protected void onStop() {
        foreground=false;
        synchronized(resumeStoreLock){resume.pause(android.os.SystemClock.elapsedRealtime(),client.isConnected());saveResumeCheckpoint();}
        resuming=false;
        super.onStop();editor.discard();showKeyboard(false);
        officialClient.close();ownClient.close();
        if(web!=null)web.evaluateJavascript("window.mandoSuspended && window.mandoSuspended()",null);
    }

    @Override protected void onDestroy() {
        destroyed = true;resume.clear();
        officialClient.close();ownClient.close(); worker.shutdownNow();
        if (web != null) { web.removeJavascriptInterface("MandoNative"); web.destroy(); }
        super.onDestroy();
    }
}
