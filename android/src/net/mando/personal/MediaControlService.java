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
    private android.media.session.MediaSession mediaSession;
    private void sendMediaKey(String key) {
        if(!java.util.Arrays.asList("PREVIOUS","PLAY_PAUSE","NEXT","VOLUME_DOWN","VOLUME_UP").contains(key))return;
        submit(()->CLIENT.action(new JSONObject().put("type","key").put("key",key).put("modifiers",new JSONArray())));
    }
    private void createMediaSession() {
        mediaSession=new android.media.session.MediaSession(this,"Mando-PC");
        mediaSession.setFlags(android.media.session.MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|android.media.session.MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new android.media.session.MediaSession.Callback(){
            @Override public void onPlay(){sendMediaKey("PLAY_PAUSE");}
            @Override public void onPause(){sendMediaKey("PLAY_PAUSE");}
            @Override public void onSkipToPrevious(){sendMediaKey("PREVIOUS");}
            @Override public void onSkipToNext(){sendMediaKey("NEXT");}
            @Override public void onCustomAction(String action,android.os.Bundle extras){
                if("mando.volume.down".equals(action))sendMediaKey("VOLUME_DOWN");
                else if("mando.volume.up".equals(action))sendMediaKey("VOLUME_UP");
            }
        });
        // PC playback state is unknown: expose a ready/paused remote, never claim
        // that this phone is playing audio and never request Android audio focus.
        mediaSession.setPlaybackState(new android.media.session.PlaybackState.Builder()
            .setActions(android.media.session.PlaybackState.ACTION_PLAY|android.media.session.PlaybackState.ACTION_PAUSE|
                android.media.session.PlaybackState.ACTION_PLAY_PAUSE|android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS|
                android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT)
            .addCustomAction("mando.volume.down","Bajar volumen del PC",R.drawable.ic_volume_down)
            .addCustomAction("mando.volume.up","Subir volumen del PC",R.drawable.ic_volume_up)
            .setState(android.media.session.PlaybackState.STATE_PAUSED,android.media.session.PlaybackState.PLAYBACK_POSITION_UNKNOWN,0).build());
        mediaSession.setMetadata(new android.media.MediaMetadata.Builder()
            .putString(android.media.MediaMetadata.METADATA_KEY_TITLE,"Control multimedia del PC")
            .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST,"Mando · PC conectado")
            .putBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART,notificationLogo()).build());
    }

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
            .setStyle(new Notification.MediaStyle().setMediaSession(mediaSession.getSessionToken()).setShowActionsInCompactView(0,1,2))
            .build();
    }
    @Override public void onCreate() {
        super.onCreate();
        AdbDiagnostics.init(this);
        createMediaSession();
        NotificationChannel channel = new NotificationChannel(CHANNEL,"PC conectado",NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Controles multimedia mientras exista conexión con el PC");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
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
            catch (OwnClient.ActionRejected rejected) {
                if (!CLIENT.isConnected()) handler.post(() -> {if(stamp==generation)finish();});
            }
            catch(Exception error) {handler.post(() -> {if(stamp==generation)finish();});}
        });} catch(java.util.concurrent.RejectedExecutionException busy) { /* discard, never replay */ }
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null || !retained || !CLIENT.isConnected()) {finish();return START_NOT_STICKY;}
        sessionGeneration=generation;
        mediaSession.setActive(true);
        try {startForeground(NOTIFICATION, notification());}
        catch(RuntimeException unavailable){finish();return START_NOT_STICKY;}
        handler.removeCallbacks(heartbeat);handler.postDelayed(heartbeat,10000);
        String action=intent.getAction();
        if(action!=null) {
            String key=MediaNotificationKey.parse(action);
            if(key!=null)sendMediaKey(key);
        }
        return START_NOT_STICKY;
    }
    private void finish() {
        if(finished)return;
        finished=true;retained=false;CLIENT.close();
        handler.removeCallbacksAndMessages(null);
        if(mediaSession!=null)mediaSession.setActive(false);
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }
    @Override public void onDestroy() {
        if(sessionGeneration==generation){retained=false;CLIENT.close();}
        handler.removeCallbacksAndMessages(null);
        if(mediaSession!=null){mediaSession.setActive(false);mediaSession.release();mediaSession=null;}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) {return null;}
}
