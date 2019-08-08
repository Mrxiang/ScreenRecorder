package com.demo.test;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.media.projection.IMediaProjection;
import android.media.projection.IMediaProjectionManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.StrictMode;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.BidiFormatter;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import android.media.MediaCodecInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.os.Build.VERSION_CODES.M;


import static com.demo.test.ScreenRecorder.AUDIO_AAC;
import static com.demo.test.ScreenRecorder.VIDEO_AVC;


import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import com.demo.test.R;



public class ScreenRecorderService extends Service {



    private String TAG="xshx.ScreenRecorderService";
    private LinearLayout    dialogLayout;
    private LinearLayout    sureDialogLayout;
    private Button          nevigationButton;
    private Button          positiveButton;
    private LayoutInflater  inflater;

    private MediaProjectionManager  mMediaProjectionManager;
    private Notifications           mNotifications;
    private CheckBox                mShowDialog;
    private ToggleButton    mShowRecordToggle;
    private MediaCodecInfo[] mAvcCodecInfos; // avc codecs
    private MediaCodecInfo[] mAacCodecInfos; // aac codecs
    private ScreenRecorder mRecorder;

    private CheckBox        mCheckBox;
    private Button          sureButton;
    private Button          cancelButton;
    private static final String SCREEN_RECORDER_ON = "screen_recorder_on";
    private static final int REQUEST_MEDIA_PROJECTION = 1;
    private static final int REQUEST_PERMISSIONS = 2;
    private static final float MAX_APP_NAME_SIZE_PX = 500f;

    private boolean   toggleRecordDialog ;
    private boolean   toggleSureDialog ;


    private Dialog mRecordDialog;
    private AlertDialog  mSureDialog;
    private WindowManager.LayoutParams  layoutParams;

    public static final  boolean   widthAudio= true;
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: ");
        toggleRecordDialog = PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .getBoolean("toggleRecordDialog", false);
        //
        Log.d(TAG, "onCreate: "+toggleRecordDialog);
        if( toggleRecordDialog ){
            toggleSureDialog = PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                    .getBoolean("toggleSureDialog", false);
            //
            Log.d(TAG, "onCreate: "+toggleSureDialog);
            if(toggleSureDialog ){
                startWrapRecorder();
            }else{

                showSureDialog( );
            }

        }else{

            showRecordDialog( );


        }




    }
    public IBinder onBind(Intent intent) {

        Log.d(TAG, "Bind request of intent: " + intent);

        return null;
    }
    
    private  void showRecordDialog( ){
        Log.d(TAG, "showRecordDialog: ");
        inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
        dialogLayout = (LinearLayout) inflater.inflate(R.layout.hisense_screen_recorder, null);
        layoutParams = new WindowManager.LayoutParams();
        layoutParams.format= PixelFormat.RGBA_8888;

        mRecordDialog = new RecordDialog(getBaseContext(), R.style.record_dialog);
        mRecordDialog.setContentView(dialogLayout,layoutParams);
        mRecordDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        mRecordDialog.setCancelable( false );
        mRecordDialog.show();
        nevigationButton = dialogLayout.findViewById( R.id.record_button );
        nevigationButton.setOnClickListener(this::onButtonClick);
        positiveButton = dialogLayout.findViewById( R.id.cancel_record );
        positiveButton.setOnClickListener(this::onButtonClick);

//        mShowRecordToggle = dialogLayout.findViewById()

        mShowDialog = dialogLayout.findViewById( R.id.show_dialog );
        mShowDialog.setOnCheckedChangeListener( new CompoundButton.OnCheckedChangeListener(){
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked){
                Log.d(TAG, "onCheckedChanged: "+isChecked);
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).
                            edit().putBoolean("toggleRecordDialog", isChecked).commit();

            }

        });



    }

    private TextView mSureTitle;

    private void showSureDialog( ){
        Log.d(TAG, "showSureDialog: ");
        TextPaint paint = new TextPaint();
        paint.setTextSize(42);
        String label = getResources().getString(R.string.app_label).toString();
        String unsanitizedAppName = TextUtils.ellipsize(label,
                paint, MAX_APP_NAME_SIZE_PX, TextUtils.TruncateAt.END).toString();
        String appName = BidiFormatter.getInstance().unicodeWrap(unsanitizedAppName);

        String actionText = getString(R.string.media_projection_dialog_text, appName);
        SpannableString message = new SpannableString(actionText);

        int appNameIndex = actionText.indexOf(appName);
        if (appNameIndex >= 0) {
            message.setSpan(new StyleSpan(Typeface.BOLD),
                    appNameIndex, appNameIndex + appName.length(), 0);
        }

        inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
        sureDialogLayout = (LinearLayout) inflater.inflate(R.layout.hisense_sure_dialog, null);


        mSureDialog = new AlertDialog.Builder(this).setView(sureDialogLayout).create();
        mSureDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        mSureDialog.show();

        mSureTitle = sureDialogLayout.findViewById( R.id.sure_dialog_title );
        mSureTitle.setText( message );
        mCheckBox = sureDialogLayout.findViewById( R.id.remember);

        cancelButton = sureDialogLayout.findViewById( R.id.cancel_sure);
        cancelButton.setOnClickListener( this::onButtonClick);
        sureButton = sureDialogLayout.findViewById( R.id.sure_button);
        sureButton.setOnClickListener( this::onButtonClick);

        mCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Log.d(TAG, "onCheckedChanged: "+isChecked);
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).
                        edit().putBoolean("toggleSureDialog", isChecked).commit();
            }
        });

    }

    private void onButtonClick(View v) {
        switch ( v.getId() ){
            case R.id.record_button:
                mRecordDialog.dismiss();
                showSureDialog( );
                break;
            case R.id.cancel_record:
                mRecordDialog.dismiss( );
                break;
            case R.id.cancel_sure:
                mSureDialog.dismiss();
                break;
            case R.id.sure_button:
                mSureDialog.dismiss();
                startWrapRecorder();
                break;
            default:
                break;
        }



    }

    private void startRecorder() {
        Log.d(TAG, "startRecorder: ");
        if (mRecorder == null) return;
        Settings.Global.putInt(this.getContentResolver(), SCREEN_RECORDER_ON, 1);
        mRecorder.start();
        registerReceiver(mStopActionReceiver, new IntentFilter(ACTION_STOP));
    }
    private void stopRecorder() {
        Log.d(TAG, "stopRecorder: ");
        mNotifications.clear();
        if (mRecorder != null) {
            mRecorder.quit();
        }
        mRecorder = null;
        Settings.Global.putInt(this.getContentResolver(), SCREEN_RECORDER_ON, 0);
        try {
            unregisterReceiver(mStopActionReceiver);
        } catch (Exception e) {
            //ignored
        }
        stopSelf();
    }
    private void cancelRecorder() {
        if (mRecorder == null) return;
        Toast.makeText(this, "Permission denied! Screen recorder is cancel", Toast.LENGTH_SHORT).show();
        stopRecorder();
    }
    @TargetApi(M)
    private void requestPermissions() {
        String[] permissions = widthAudio
                ? new String[]{WRITE_EXTERNAL_STORAGE, RECORD_AUDIO}
                : new String[]{WRITE_EXTERNAL_STORAGE};
        boolean showRationale = false;
        for (String perm : permissions) {
            showRationale |= getPackageManager().shouldShowRequestPermissionRationale(perm);
        }
        if (!showRationale) {
//            requestPermissions(permissions, REQUEST_PERMISSIONS);
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage("Using your mic to record audio and your sd card to save video file")
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create()
                .show();
    }
    private boolean hasPermissions() {
        PackageManager pm = getPackageManager();
        String packageName = getPackageName();
        int granted = (widthAudio ? pm.checkPermission(RECORD_AUDIO, packageName) : PackageManager.PERMISSION_GRANTED)
                | pm.checkPermission(WRITE_EXTERNAL_STORAGE, packageName);
        return granted == PackageManager.PERMISSION_GRANTED;
    }
    private static void logCodecInfos(MediaCodecInfo[] codecInfos, String mimeType) {
        for (MediaCodecInfo info : codecInfos) {
            StringBuilder builder = new StringBuilder(512);
            MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(mimeType);
            builder.append("Encoder '").append(info.getName()).append('\'')
                    .append("\n  supported : ")
                    .append(Arrays.toString(info.getSupportedTypes()));
            MediaCodecInfo.VideoCapabilities videoCaps = caps.getVideoCapabilities();
            if (videoCaps != null) {
                builder.append("\n  Video capabilities:")
                        .append("\n  Widths: ").append(videoCaps.getSupportedWidths())
                        .append("\n  Heights: ").append(videoCaps.getSupportedHeights())
                        .append("\n  Frame Rates: ").append(videoCaps.getSupportedFrameRates())
                        .append("\n  Bitrate: ").append(videoCaps.getBitrateRange());
                if (VIDEO_AVC.equals(mimeType)) {
                    MediaCodecInfo.CodecProfileLevel[] levels = caps.profileLevels;

                    builder.append("\n  Profile-levels: ");
                    for (MediaCodecInfo.CodecProfileLevel level : levels) {
                        builder.append("\n  ").append(Utils.avcProfileLevelToString(level));
                    }
                }
                builder.append("\n  Color-formats: ");
                for (int c : caps.colorFormats) {
                    builder.append("\n  ").append(Utils.toHumanReadable(c));
                }
            }
            MediaCodecInfo.AudioCapabilities audioCaps = caps.getAudioCapabilities();
            if (audioCaps != null) {
                builder.append("\n Audio capabilities:")
                        .append("\n Sample Rates: ").append(Arrays.toString(audioCaps.getSupportedSampleRates()))
                        .append("\n Bit Rates: ").append(audioCaps.getBitrateRange())
                        .append("\n Max channels: ").append(audioCaps.getMaxInputChannelCount());
            }
            Log.i("@@@", builder.toString());
        }
    }
    static final String ACTION_STOP = "net.yrom.screenrecorder.action.STOP";

    private BroadcastReceiver mStopActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            File file = new File(mRecorder.getSavedPath());
            if (ACTION_STOP.equals(intent.getAction())) {
                stopRecorder();
            }
            String str = context.getString(R.string.stop_recording_save_file, file.toString());
            Toast.makeText(context, str, Toast.LENGTH_LONG).show();
            StrictMode.VmPolicy vmPolicy = StrictMode.getVmPolicy();
            try {
                // disable detecting FileUriExposure on public file
                StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
                //viewResult(file);
            } finally {
                StrictMode.setVmPolicy(vmPolicy);
            }
        }

        private void viewResult(File file) {
            Intent view = new Intent(Intent.ACTION_VIEW);
            view.addCategory(Intent.CATEGORY_DEFAULT);
            view.setDataAndType(Uri.fromFile(file), VIDEO_AVC);
            view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(view);
            } catch (ActivityNotFoundException e) {
                // no activity can open this video
            }
        }
    };


    public static final int RESULT_OK           = -1;
    private  void startWrapRecorder( ){
        Log.d(TAG, "startWrapRecorder: ");

        mMediaProjectionManager = (MediaProjectionManager) getApplicationContext().getSystemService(MEDIA_PROJECTION_SERVICE);
        mNotifications = new Notifications(getApplicationContext());
        Utils.findEncodersByTypeAsync(VIDEO_AVC, infos -> {
            logCodecInfos(infos, VIDEO_AVC);
            mAvcCodecInfos = infos;

        });
        Utils.findEncodersByTypeAsync(AUDIO_AAC, infos -> {
            logCodecInfos(infos, AUDIO_AAC);
            mAacCodecInfos = infos;

        });

        Intent data = getMediaProjectionIntent();
        Log.d(TAG, "startWrapRecorder: "+data.toString());
        MediaProjection mediaProjection = mMediaProjectionManager.getMediaProjection(RESULT_OK, data);
        if (mediaProjection == null) {
            Log.e("@@", "media projection is null");
            return;
        }

        VideoEncodeConfig video = createVideoConfig();
        AudioEncodeConfig audio = createAudioConfig(); // audio can be null
        if (video == null) {
            Toast.makeText(getBaseContext(), "Create ScreenRecorder failure", 1000).show();
            mediaProjection.stop();
            return;
        }

        File dir = getSavingDir();
        if (!dir.exists() && !dir.mkdirs()) {
            cancelRecorder();
            return;
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
        final File file = new File(dir, "Screen-" + format.format(new Date())
                + "-" + video.width + "x" + video.height + ".mp4");
        Log.d("@@", "Create recorder with :" + video + " \n " + audio + "\n " + file);
        mRecorder = newRecorder(mediaProjection, video, audio, file);
        if (hasPermissions()) {

            startRecorder();

            Intent intent = new Intent( getBaseContext(), FloatWindowService.class );
            startServiceAsUser( intent, UserHandle.CURRENT );
        } else {
            cancelRecorder();
        }
    }
    private ScreenRecorder newRecorder(MediaProjection mediaProjection, VideoEncodeConfig video,
                                                               AudioEncodeConfig audio, File output) {
        ScreenRecorder r = new ScreenRecorder(video, audio,
                1, mediaProjection, output.getAbsolutePath());
        r.setCallback(new ScreenRecorder.Callback() {
            long startTime = 0;

            @Override
            public void onStop(Throwable error) {
                Log.d(TAG, "onStop: ",new Throwable());
                stopRecorder();
                if (error != null) {
                    Toast.makeText(getBaseContext(),"Recorder error ! See logcat for more details", 1000).show();
                    error.printStackTrace();
                    output.delete();
                } else {
                    Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                            .addCategory(Intent.CATEGORY_DEFAULT)
                            .setData(Uri.fromFile(output));
                    sendBroadcast(intent);
                }
            }

            @Override
            public void onStart() {
                Log.d(TAG, "onStart: ");
                mNotifications.recording(0);
            }

            @Override
            public void onRecording(long presentationTimeUs) {
                Log.d(TAG, "onRecording: "+presentationTimeUs);
                if (startTime <= 0) {
                    startTime = presentationTimeUs;
                }
                long time = (presentationTimeUs - startTime) / 1000;
                mNotifications.recording(time);
            }
        });
        return r;
    }

    private AudioEncodeConfig createAudioConfig() {
//        if (!mAudioToggle.isChecked()) return null;
        if (!widthAudio) return null;
        String codec = "OMX.google.aac.encoder"; //getSelectedAudioCodec();
        if (codec == null) {
            return null;
        }
        int bitrate = 80000; //getSelectedAudioBitrate();
        int samplerate = 44100; //getSelectedAudioSampleRate();
        int channelCount = 1; //getSelectedAudioChannelCount();
        int profile = 1;//getSelectedAudioProfile();
        Log.i("ScreenRecorder", "createAudioConfig codec=" + codec + ",bitrate=" + bitrate + ",samplerate=" + samplerate + ",channelCount=" + channelCount
                + ",profile=" + profile);
        return new AudioEncodeConfig(codec, ScreenRecorder.AUDIO_AAC, bitrate, samplerate, channelCount, profile);
    }

    private VideoEncodeConfig createVideoConfig() {
        final String codec = "OMX.MTK.VIDEO.ENCODER.AVC"; //getSelectedVideoCodec();
        if (codec == null) {
            // no selected codec ??
            return null;
        }
        // video size
        DisplayMetrics metric = new DisplayMetrics();
        ((WindowManager)(getBaseContext().getSystemService( Context.WINDOW_SERVICE))).getDefaultDisplay().getRealMetrics(metric);
        int w = metric.widthPixels;
        int h = metric.heightPixels;
        Log.i("ScreenRecorder", "createVideoConfig w=" + w + ",h=" + h);
/*
        int[] selectedWithHeight = getSelectedWithHeight();
        boolean isLandscape = isLandscape();
        int width = selectedWithHeight[isLandscape ? 0 : 1];
        int height = selectedWithHeight[isLandscape ? 1 : 0];
*/
        int width = w;
        int height = h;
        int framerate = 20; //getSelectedFramerate();
        int iframe = 1; //getSelectedIFrameInterval();
        int bitrate = 800000; //getSelectedVideoBitrate();
        MediaCodecInfo.CodecProfileLevel profileLevel = null; //getSelectedProfileLevel();
        Log.i("ScreenRecorder", "createVideoConfig w=" + w + ",h=" + h + ",bitrate=" + bitrate + ",framerate=" + framerate + ",iframe=" + iframe + ",codec=" + codec
                + ",profileLevel=" + profileLevel);
        return new VideoEncodeConfig(width, height, bitrate,
                framerate, iframe, codec, ScreenRecorder.VIDEO_AVC, profileLevel);
    }

    private static File getSavingDir() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "ScreenCaptures");
    }

    private IMediaProjectionManager mService;

    private Intent getMediaProjectionIntent( ){
        PackageManager packageManager = getPackageManager();
        ApplicationInfo aInfo;
        int mUid;

        try {
            aInfo = packageManager.getApplicationInfo(getPackageName(), 0);
            mUid = aInfo.uid;
            return getMediaProjectionIntent( mUid, getPackageName(), false);

        } catch (Exception e) {
            Log.e(TAG, "unable to look up package name", new Throwable());
            return null;
        }
    }
    private Intent getMediaProjectionIntent(int uid, String packageName, boolean permanentGrant)
            throws RemoteException {
        IBinder b = ServiceManager.getService(MEDIA_PROJECTION_SERVICE);
        mService = IMediaProjectionManager.Stub.asInterface(b);
        IMediaProjection projection = mService.createProjection(uid, packageName,
                MediaProjectionManager.TYPE_SCREEN_CAPTURE, permanentGrant);
        Intent intent = new Intent();
        intent.putExtra(MediaProjectionManager.EXTRA_MEDIA_PROJECTION, projection.asBinder());
        return intent;
    }
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: ");
    }
}
