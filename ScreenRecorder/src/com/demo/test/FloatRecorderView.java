package com.demo.test;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;


public class FloatRecorderView extends LinearLayout {

    private String TAG = "xshx.FloatRecorderView";
    private LinearLayout    floatRecordLayout;
    private ImageView       recordIndicator;
    private TextView        floatRecordText;


    private  float          xInScreen;
    private  float          yInScreen;

    private  float          xInView;
    private  float          yInView;

    public   int            viewWidth;
    public   int            viewHeight;


    //状态栏高度.
    int statusBarHeight = -1;

    private WindowManager.LayoutParams   layoutParams;
    private WindowManager       windowManager;
    public FloatRecorderView( Context context){

        super( context );

        // 新建悬浮窗控件
        windowManager = (WindowManager)context.getSystemService( Context.WINDOW_SERVICE);
        LayoutInflater.from(context).inflate( R.layout.float_recorder_layout, this);

        floatRecordLayout = findViewById( R.id.float_record_layout);
        viewWidth = floatRecordLayout.getLayoutParams().width;
        viewHeight = floatRecordLayout.getLayoutParams().height;

        //用于检测状态栏高度.
        int resourceId = getResources().getIdentifier("status_bar_height","dimen","android");
        if (resourceId > 0)
        {
            statusBarHeight = getResources().getDimensionPixelSize(resourceId);
        }
        Log.i(TAG,"状态栏高度为:" + statusBarHeight);


    }
    public void setParams(WindowManager.LayoutParams params ){
        layoutParams = params;
    }
    public void UpdateFloatRecorderView(int timeCounter ){
        Log.d(TAG, "updateFloatingWindow: ");
        recordIndicator = findViewById( R.id.record_indicator);
        if( recordIndicator.getVisibility() == View.VISIBLE ){
            recordIndicator.setVisibility( View.INVISIBLE );
        }else{
            recordIndicator.setVisibility( View.VISIBLE );
        }

        floatRecordText = findViewById( R.id.float_record_text);
        floatRecordText.setText(  formateTime( timeCounter/2 ) );

    }


    private String formateTime( int timeCounter){
        int hour = timeCounter/(60*60);
        int min  = (timeCounter/60)%60;
        int second = timeCounter%60;
        String time = String.format(getResources().getString(R.string.time_counter), hour, min, second);
        return time;
    }

    public boolean onTouchEvent( MotionEvent event){
        Log.d(TAG, "onTouchEvent: "+MotionEvent.actionToString( event.getAction())+"\n"+event.toString());
        switch ( event.getAction() ){
            case MotionEvent.ACTION_DOWN:
                xInView = event.getX();
                yInView = event.getY();
                xInScreen = event.getRawX();
                yInScreen = event.getRawY();
                updateViewPosition();
                break;
            case MotionEvent.ACTION_MOVE:
                xInScreen = event.getRawX();
                yInScreen = event.getRawY();
                updateViewPosition();
                break;
            case MotionEvent.ACTION_UP:
                break;
            default:
                break;
        }
        return false;
    }


    public void updateViewPosition( ){
        Log.d(TAG, "updateViewPosition: ");
        layoutParams.x = (int)(xInScreen -xInView);
        layoutParams.y = (int)(yInScreen -yInView - statusBarHeight);
        Log.d(TAG, "updateViewPosition: "+layoutParams.x+" : "+ layoutParams.y);
        windowManager.updateViewLayout(this, layoutParams );

    }
}
