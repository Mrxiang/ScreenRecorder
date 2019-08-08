package com.demo.test;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.UserHandle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
public class MainActivity extends Activity {


    private Button startRecordService;
    private Button startRecordActivity;

    private String TAG = "xshx.MainActivity";

    protected ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // TODO Auto-generated method stub
            Log.i(TAG, "onServiceConnected() -- ");

            //iBinder = service ;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // TODO Auto-generated method stub
            Log.i(TAG, "onServiceDisconnected()");
        }
    };


    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.main_activty);


        startRecordService = findViewById(R.id.startRecordService);
        startRecordService.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                try {
                    Intent intent = new Intent(MainActivity.this, ScreenRecorderService.class);
                    startServiceAsUser( intent, UserHandle.CURRENT);

//                    Intent intent = new Intent(MainActivity.this, ScreenRecorderActivity.class);
//                    startActivity(intent);
                    moveTaskToBack(true);
                } catch (Exception e) {
                    Log.d(TAG, "onClick: ", new Throwable());
                }
            }
        });

        startRecordActivity = findViewById(R.id.startRecordActivity);
        startRecordActivity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                try {
//                    Intent intent = new Intent(MainActivity.this, ScreenRecorderService.class);
//                    startService( intent);

                    Intent intent = new Intent(MainActivity.this, ScreenRecorderActivity.class);
                    startActivity(intent);
//                    moveTaskToBack(true);
                } catch (Exception e) {
                    Log.d(TAG, "onClick: ", new Throwable());
                }
            }
        });

    }

    protected void onDestroy() {
        Log.d(TAG, "onDestroy: ");
        super.onDestroy();
    }
}
