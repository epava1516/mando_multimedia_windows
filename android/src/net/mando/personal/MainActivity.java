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
    private final RemoteClient client = new RemoteClient();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(32));
    private volatile boolean destroyed;
    private volatile boolean foreground;
    private InfraredController infrared;
    private volatile boolean imeVisible;
    private boolean zoomSetupShowing;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        infrared = new InfraredController(this);
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
        if(destroyed || !client.isConnected())return;
        try {
            worker.execute(() -> {
                try {client.action(action);}
                catch(Exception error) {
                    runOnUiThread(() -> {
                        if(!destroyed) {
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
            try {
                worker.execute(() -> {
                    JSONObject result = new JSONObject();
                    try {
                        JSONObject request = new JSONObject(raw);
                        String path = request.getString("path");
                        JSONObject data = request.optJSONObject("data");
                        if ("/api/ir/status".equals(path)) {
                            result.put("available", infrared.isAvailable());
                            result.put("profile", "LGTV1");
                        } else if ("/api/ir/send".equals(path)) {
                            if (!foreground || android.os.SystemClock.elapsedRealtime() - requestedAt > 750)
                                throw new IOException("La señal IR ha caducado. Pulsa de nuevo.");
                            if (data == null) throw new IOException("Falta el botón infrarrojo");
                            infrared.send(data.getString("command"));
                            result.put("ok", true);
                        } else if ("/api/status".equals(path)) {
                            result.put("paired", client.isConnected());
                            result.put("dryRun", false);
                            result.put("imeVisible",imeVisible);
                        } else if ("/api/zoom-setup".equals(path)) {
                            runOnUiThread(() -> showZoomSetup());result.put("ok",true);
                        } else if ("/api/lock-setup".equals(path)) {
                            runOnUiThread(() -> showLockSetup());result.put("ok",true);
                        } else if ("/api/lock".equals(path)) {
                            if(!getPreferences(MODE_PRIVATE).getBoolean("lockHelperInstalled",false)) {
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
                            result.put("irTabVisible",getPreferences(MODE_PRIVATE).getBoolean("irTabVisible",infrared.isAvailable()));
                            result.put("sensitivity",getPreferences(MODE_PRIVATE).getFloat("sensitivity",0.65f));
                            result.put("lockHelperInstalled",getPreferences(MODE_PRIVATE).getBoolean("lockHelperInstalled",false));
                            result.put("zoomHelperInstalled",getPreferences(MODE_PRIVATE).getBoolean("zoomHelperInstalled",false));
                        } else if ("/api/keyboard".equals(path)) {
                            final boolean visible=data!=null && data.optBoolean("visible",false);
                            if(visible && !client.isConnected())throw new IOException("Conecta primero con el PC");
                            runOnUiThread(() -> showKeyboard(visible));result.put("ok",true);
                        } else if ("/api/pair".equals(path)) {
                            if (data == null) throw new IOException("Falta la dirección del PC");
                            client.connect(wifi(), data.getString("host"));
                            result.put("ok", true);
                            result.put("version", client.serverVersion());
                        } else if ("/api/scan".equals(path)) {
                            ConnectivityManager manager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
                            Network network=wifi();
                            result.put("servers",client.scan(network,manager.getLinkProperties(network)));
                        } else if ("/api/logout".equals(path)) {
                            client.close(); result.put("ok", true);
                        } else if ("/api/action".equals(path)) {
                            if (data == null) throw new IOException("Falta la acción");
                            if ("zoom".equals(data.optString("type")) &&
                                    !getPreferences(MODE_PRIVATE).getBoolean("zoomHelperInstalled",false)) {
                                runOnUiThread(() -> showZoomSetup());result.put("needsSetup",true);
                            } else {
                                if (("zoom".equals(data.optString("type")) || "task_view".equals(data.optString("type"))) &&
                                        (!foreground || android.os.SystemClock.elapsedRealtime()-requestedAt>750))
                                    result.put("expired",true);
                                else {client.action(data); result.put("ok", true);}
                            }
                        } else throw new IOException("Acción no permitida");
                    } catch (Exception e) {
                        try { result.put("error", safeMessage(e)); } catch (Exception ignored) { }
                    }
                    reply(id, result);
                });
            } catch (java.util.concurrent.RejectedExecutionException busy) {
                try { reply(id, new JSONObject().put("error", "Demasiadas acciones pendientes. Espera un momento.")); }
                catch (Exception ignored) { }
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
        super.onStart();
        foreground = true;
    }

    @Override protected void onStop() {
        foreground = false;
        super.onStop();
        editor.discard();
        showKeyboard(false);
        client.close(); // No commands remain active when leaving the controller.
        if (web != null) web.evaluateJavascript("window.mandoDisconnected && window.mandoDisconnected()", null);
    }

    @Override protected void onDestroy() {
        destroyed = true;
        client.close(); worker.shutdownNow();
        if (web != null) { web.removeJavascriptInterface("MandoNative"); web.destroy(); }
        super.onDestroy();
    }
}
