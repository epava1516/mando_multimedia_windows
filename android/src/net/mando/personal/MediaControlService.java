package net.mando.personal;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Owns the live connection while the control activity is in the background. No credentials in intents. */
public final class MediaControlService extends Service {
    static final OwnClient CLIENT = new OwnClient();
    private static final String CHANNEL = "mando-connected";
    private static final int NOTIFICATION = 21;
    private static volatile boolean retained;
    private static volatile long generation;
    private long sessionGeneration;
    static final ThreadPoolExecutor IO = new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(32));
    static { IO.allowCoreThreadTimeOut(true); }
    private final Handler handler = new Handler(android.os.Looper.getMainLooper());
    private final ThreadPoolExecutor worker = IO;
    private volatile boolean finished;

    static boolean holdsConnection() { return retained && CLIENT.isConnected(); }
    static boolean notificationsAllowed(Context context) {
        return (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                && context.getSystemService(NotificationManager.class).areNotificationsEnabled();
    }
    static void attach(Context context) {
        if (!CLIENT.isConnected() || !notificationsAllowed(context)) return;
        retained = true;generation++;
        try { context.startForegroundService(new Intent(context,MediaControlService.class)); }
        catch (RuntimeException unavailable) { retained = false; }
    }
    static void detach(Context context) {
        retained = false;
        context.stopService(new Intent(context,MediaControlService.class));
    }
    private PendingIntent command(String key, int id) {
        Intent intent = new Intent(this, MediaControlService.class).setAction("net.mando.media."+key);
        return PendingIntent.getService(this,id,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private android.graphics.Bitmap notificationLogo() {
        android.graphics.drawable.Drawable logo=getDrawable(R.drawable.ic_mando);
        int size=Math.round(64*getResources().getDisplayMetrics().density);
        android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(size,size,android.graphics.Bitmap.Config.ARGB_8888);
        logo.setBounds(0,0,size,size);logo.draw(new android.graphics.Canvas(image));
        return image;
    }
    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Mando").setContentText("PC conectado").setSubText("Conectado")
            .setColor(0xffa8b9ff).setColorized(true).setShowWhen(false).setLargeIcon(notificationLogo())
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(new Notification.Action.Builder(R.drawable.ic_media_previous,"Anterior",command("PREVIOUS",1)).build())
            .addAction(new Notification.Action.Builder(R.drawable.ic_media_toggle,"Play / pausa",command("PLAY_PAUSE",2)).build())
            .addAction(new Notification.Action.Builder(R.drawable.ic_media_next,"Siguiente",command("NEXT",3)).build())
            .addAction(new Notification.Action.Builder(R.drawable.ic_volume_down,"Bajar volumen",command("VOLUME_DOWN",4)).build())
            .addAction(new Notification.Action.Builder(R.drawable.ic_volume_up,"Subir volumen",command("VOLUME_UP",5)).build())
            .setStyle(new Notification.MediaStyle().setShowActionsInCompactView(0,1,2))
            .build();
    }
    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL,"PC conectado",NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Controles multimedia mientras exista conexión con el PC");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    private final Runnable heartbeat = new Runnable() {
        public void run() {
            if(finished)return;
            if(!retained || !CLIENT.isConnected() || !notificationsAllowed(MediaControlService.this)) {finish();return;}
            submit(() -> {CLIENT.refreshStatus();});
            handler.postDelayed(this,10000);
        }
    };
    interface Operation {void run() throws Exception;}
    private void submit(Operation operation) {
        final long requested = android.os.SystemClock.elapsedRealtime();
        final long stamp = generation;
        try {worker.execute(() -> {
            if(finished || !retained || stamp!=generation || !CLIENT.isConnected())return;
            if(android.os.SystemClock.elapsedRealtime()-requested>1500)return;
            try {operation.run();}
            catch(Exception error) {handler.post(() -> {if(stamp==generation)finish();});}
        });} catch(java.util.concurrent.RejectedExecutionException busy) { /* discard, never replay */ }
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null || !retained || !CLIENT.isConnected()) {finish();return START_NOT_STICKY;}
        sessionGeneration=generation;
        try {startForeground(NOTIFICATION, notification());}
        catch(RuntimeException unavailable){finish();return START_NOT_STICKY;}
        handler.removeCallbacks(heartbeat);handler.postDelayed(heartbeat,10000);
        String action=intent.getAction();
        if(action!=null) {
            String key=MediaNotificationKey.parse(action);
            if(key!=null)submit(() -> CLIENT.action(new JSONObject().put("type","key").put("key",key).put("modifiers",new JSONArray())));
        }
        return START_NOT_STICKY;
    }
    private void finish() {
        if(finished)return;
        finished=true;retained=false;CLIENT.close();
        handler.removeCallbacksAndMessages(null);
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }
    @Override public void onDestroy() {
        if(sessionGeneration==generation){retained=false;CLIENT.close();}
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) {return null;}
}
