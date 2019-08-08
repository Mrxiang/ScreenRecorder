package com.demo.test;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;


public class FloatWindowService extends Service {

    public Handler handler = new Handler();
    public static final int      CREATE_VIEW = 1;
    public static final int      UPDATE_VIEW = 2;
    public static final int      DESTROY_VIEW = 3;
    private FloatRecorderView floatRecordLayout;
    private Button                      stopRecord;
    private WindowManager               windowManager;
    private WindowManager.LayoutParams  layoutParams;

    private GlobalSetting mSetting;
    private static final String SCREEN_RECORDER_ON = "screen_recorder_on";

    private String TAG="xshx.FloatWindowService";

    private int    timeCounter;
    public  void onCreate( ){
        Log.d(TAG, "onCreate: ");
        super.onCreate();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        handler = new Handler(){
            public void handleMessage( Message msg){
                switch ( msg.what ){
                    case CREATE_VIEW:
                        createFloatingWindow();
                        handler.sendEmptyMessage( UPDATE_VIEW );
                        break;
                    case UPDATE_VIEW:
                        updateFloatingWindow();
                        handler.sendEmptyMessageDelayed( UPDATE_VIEW, 500);
                        break;
                    case DESTROY_VIEW:
                        handler.removeCallbacksAndMessages( null );
                        destroyFloatingWindow();
                        break;
                    default:
                        break;
                }
            }

        };

        mSetting = new GlobalSetting(this, handler, SCREEN_RECORDER_ON) {
            @Override
            protected void handleValueChanged(int value) {
                    Log.d(TAG, "Received Gloable changes to " + value);
                    if( value == 0 ){

                        handler.sendEmptyMessage( DESTROY_VIEW );
                    }
            }
        };
        mSetting.setListening(true);

    }

    public IBinder onBind(Intent intent) {
        return null;
    }

    public int onStartCommand(Intent intent, int flags, int startId) {

        handler.sendEmptyMessage( CREATE_VIEW );

        return 0;
    }

    private void createFloatingWindow() {
        // 获取WindowManager服务
        // 设置LayoutParam
        Log.d(TAG, "createFloatingWindow: ");
        floatRecordLayout = new FloatRecorderView( this );
        stopRecord        = floatRecordLayout.findViewById( R.id.stop_record );
        layoutParams = new WindowManager.LayoutParams();

        layoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        layoutParams.format= PixelFormat.RGBA_8888;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        layoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        layoutParams.width = floatRecordLayout.viewWidth;
        layoutParams.height = floatRecordLayout.viewHeight;
        layoutParams.x = 10; layoutParams.y = 30;
        // 将悬浮窗控件添加到WindowManager
        floatRecordLayout.setParams(  layoutParams );
        windowManager.addView(floatRecordLayout, layoutParams);


        stopRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String ACTION_STOP = "net.yrom.screenrecorder.action.STOP";
                Intent intent = new Intent(ACTION_STOP);
                intent.setPackage("com.android.systemui");
                sendBroadcast(intent);

                handler.sendEmptyMessage( DESTROY_VIEW );
            }
        });
        timeCounter = 0;
        registerReceiver(mStopActionReceiver, new IntentFilter(ACTION_STOP));

    }
    private void updateFloatingWindow() {
        Log.d(TAG, "updateFloatingWindow: ");
        timeCounter++;
        floatRecordLayout.UpdateFloatRecorderView( timeCounter );
    }

    private void destroyFloatingWindow( ){
        Log.d(TAG, "destroyFloatingWindow: ");
        mSetting.setListening(false);
        if( floatRecordLayout != null ){
            windowManager.removeView(floatRecordLayout);
            floatRecordLayout = null;
        }
        try {
            unregisterReceiver(mStopActionReceiver);
        } catch (Exception e) {
            Log.d(TAG, "destroyFloatingWindow: "+e.toString());
        }
        stopSelf();
    }


    static final String ACTION_STOP = "net.yrom.screenrecorder.action.STOP";
    private BroadcastReceiver mStopActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_STOP.equals(intent.getAction())) {
                handler.sendEmptyMessage( DESTROY_VIEW );
            }

        }


    };

    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: ");
    }
}
