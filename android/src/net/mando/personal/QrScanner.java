package net.mando.personal;

import android.app.Activity;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Local preview only: no photos, storage, logs or network calls. */
@SuppressWarnings("deprecation")
public final class QrScanner extends Activity implements SurfaceHolder.Callback {
    private final ExecutorService decoder=Executors.newSingleThreadExecutor();
    private final AtomicBoolean decoding=new AtomicBoolean();
    private SurfaceView preview;
    private TextView hint;
    private Camera camera;
    private boolean resumed,completed;
    private int width,height;
    private long lastFrame;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(16,16,16,16);root.setBackgroundColor(0xff102323);
        hint=new TextView(this);hint.setText("Apunta al QR de Mando Server en tu PC");hint.setTextColor(0xffeff7f2);hint.setTextSize(18);hint.setPadding(8,12,8,12);
        root.addView(hint,new LinearLayout.LayoutParams(-1,-2));
        preview=new SurfaceView(this);preview.getHolder().addCallback(this);root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        Button close=new Button(this);close.setText("Cancelar");close.setOnClickListener(view->finish());root.addView(close,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},1);
    }
    @Override protected void onResume(){super.onResume();resumed=true;openCamera();}
    @Override protected void onPause(){resumed=false;releaseCamera();super.onPause();}
    @Override protected void onDestroy(){decoder.shutdownNow();super.onDestroy();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==1&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)openCamera();
        else hint.setText("Permite la cámara para leer el QR. También puedes copiar el código manualmente.");
    }
    public void surfaceCreated(SurfaceHolder holder){openCamera();}
    public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){openCamera();}
    public void surfaceDestroyed(SurfaceHolder holder){releaseCamera();}
    private void releaseCamera(){Camera previous=camera;camera=null;if(previous!=null){try{previous.setPreviewCallbackWithBuffer(null);previous.stopPreview();}catch(RuntimeException ignored){}previous.release();}}
    private void openCamera(){
        if(!resumed||completed||camera!=null||!preview.getHolder().getSurface().isValid()||checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return;
        try {
            int id=-1;Camera.CameraInfo info=new Camera.CameraInfo();
            for(int i=0;i<Camera.getNumberOfCameras();i++){Camera.getCameraInfo(i,info);if(info.facing==Camera.CameraInfo.CAMERA_FACING_BACK){id=i;break;}}
            if(id<0)throw new IllegalStateException();
            camera=Camera.open(id);Camera.Parameters parameters=camera.getParameters();
            Camera.Size selected=null;
            for(Camera.Size size:parameters.getSupportedPreviewSizes())if((long)size.width*size.height<=1280*720&&(selected==null||size.width*size.height>selected.width*selected.height))selected=size;
            if(selected==null)throw new IllegalStateException();
            width=selected.width;height=selected.height;parameters.setPreviewSize(width,height);parameters.setPreviewFormat(ImageFormat.NV21);
            List<String> focus=parameters.getSupportedFocusModes();
            if(focus!=null&&focus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            camera.setParameters(parameters);Camera.getCameraInfo(id,info);
            camera.setDisplayOrientation((info.orientation-getWindowManager().getDefaultDisplay().getRotation()*90+360)%360);
            camera.setPreviewDisplay(preview.getHolder());
            camera.setPreviewCallbackWithBuffer((data,source)->{
                if(!resumed||completed)return;
                long now=android.os.SystemClock.elapsedRealtime();
                if(now-lastFrame<180||!decoding.compareAndSet(false,true)){source.addCallbackBuffer(data);return;}
                lastFrame=now;final int w=width,h=height;
                try{decoder.execute(()->{
                    String value=QrFrames.decode(data,w,h);
                    runOnUiThread(()->{
                        decoding.set(false);
                        if(!resumed||completed||camera!=source)return;
                        if(value!=null){completed=true;setResult(RESULT_OK,new Intent().putExtra("code",value));finish();}
                        else try{source.addCallbackBuffer(data);}catch(RuntimeException ignored){}
                    });
                });}catch(java.util.concurrent.RejectedExecutionException stopped){decoding.set(false);}
            });
            camera.addCallbackBuffer(new byte[width*height*3/2]);camera.startPreview();
        }catch(Exception unavailable){releaseCamera();hint.setText("No se pudo abrir la cámara. Ciérrala en otras apps o usa el código manual.");}
    }
}
