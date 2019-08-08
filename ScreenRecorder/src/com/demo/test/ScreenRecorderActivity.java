package com.demo.test;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.MediaCodecInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ToggleButton;




import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.os.Build.VERSION_CODES.M;
import static com.demo.test.ScreenRecorder.AUDIO_AAC;
import static com.demo.test.ScreenRecorder.VIDEO_AVC;

import com.demo.test.R;

public class ScreenRecorderActivity extends Activity {
    private static final int REQUEST_MEDIA_PROJECTION = 1;
    private static final int REQUEST_PERMISSIONS = 2;
    private static final String SCREEN_RECORDER_ON = "screen_recorder_on";
    // members below will be initialized in onCreate()
    private MediaProjectionManager mMediaProjectionManager;
    private Button mButton;
    private ToggleButton mAudioToggle;
    private MediaCodecInfo[] mAvcCodecInfos; // avc codecs
    private MediaCodecInfo[] mAacCodecInfos; // aac codecs
    private Notifications mNotifications;

    private Button  mCancel;
    /**
     * <b>NOTE:</b>
     * {@code ScreenRecorder} should run in background Service
     * instead of a foreground Activity in this demonstrate.
     */
    private ScreenRecorder mRecorder;

    private  String TAG ="xshx.ScreenRecorderActivity";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate: ");
        if(android.util.FeatureOption.CF9_KS816 ){
            setTheme( R.style.Theme_AlertDialogHost);
        }
        super.onCreate(savedInstanceState);
        //xshx mod
        if( android.util.FeatureOption.CF9_KS816 ) {
            setContentView(R.layout.hisense_screen_recorder);

        }else{
            setContentView(R.layout.screen_recorder_activity);

        }
        mMediaProjectionManager = (MediaProjectionManager) getApplicationContext().getSystemService(MEDIA_PROJECTION_SERVICE);
        mNotifications = new Notifications(getApplicationContext());
        bindViews();
        Utils.findEncodersByTypeAsync(VIDEO_AVC, infos -> {
            logCodecInfos(infos, VIDEO_AVC);
            mAvcCodecInfos = infos;
/*
            SpinnerAdapter codecsAdapter = createCodecsAdapter(mAvcCodecInfos);
            mVideoCodec.setAdapter(codecsAdapter);
            restoreSelections(mVideoCodec, mVieoResolution, mVideoFramerate, mIFrameInterval, mVideoBitrate);
*/
        });
        Utils.findEncodersByTypeAsync(AUDIO_AAC, infos -> {
            logCodecInfos(infos, AUDIO_AAC);
            mAacCodecInfos = infos;
/*
            SpinnerAdapter codecsAdapter = createCodecsAdapter(mAacCodecInfos);
            mAudioCodec.setAdapter(codecsAdapter);
            restoreSelections(mAudioCodec, mAudioChannelCount);
*/
        });
        mAudioToggle.setChecked(
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                        .getBoolean(getResources().getResourceEntryName(mAudioToggle.getId()), true));

        if(android.util.FeatureOption.CF9_KS816 ){
            mCancel = findViewById( R.id.cancel_record );
            mCancel.setOnClickListener( new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    finish( );
                }
            });
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
/*
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mOrientation.setSelectedPosition(1);
        } else {
            mOrientation.setSelectedPosition(0);
        }
*/
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult: "+requestCode+" "+resultCode+" "+data.toString());
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            // NOTE: Should pass this result data into a Service to run ScreenRecorder.
            // The following codes are merely exemplary.

            MediaProjection mediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
            if (mediaProjection == null) {
                Log.e("@@", "media projection is null");
                return;
            }

            VideoEncodeConfig video = createVideoConfig();
            AudioEncodeConfig audio = createAudioConfig(); // audio can be null
            if (video == null) {
                toast("Create ScreenRecorder failure");
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
                //xshx
                if( android.util.FeatureOption.CF9_KS816 ){
                    Intent intent = new Intent( getBaseContext(), FloatWindowService.class );
                    startService( intent );
                }
            } else {
                cancelRecorder();
            }
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
                runOnUiThread(() -> stopRecorder());
                if (error != null) {
                    toast("Recorder error ! See logcat for more details");
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
                mNotifications.recording(0);
            }

            @Override
            public void onRecording(long presentationTimeUs) {
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
        if (!mAudioToggle.isChecked()) return null;
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
        return new AudioEncodeConfig(codec, AUDIO_AAC, bitrate, samplerate, channelCount, profile);
    }

    private VideoEncodeConfig createVideoConfig() {
        final String codec = "OMX.MTK.VIDEO.ENCODER.AVC"; //getSelectedVideoCodec();
        if (codec == null) {
            // no selected codec ??
            return null;
        }
        // video size
        DisplayMetrics metric = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metric);
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
                framerate, iframe, codec, VIDEO_AVC, profileLevel);
    }

    private static File getSavingDir() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "ScreenCaptures");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS) {
            int granted = PackageManager.PERMISSION_GRANTED;
            for (int r : grantResults) {
                granted |= r;
            }
            if (granted == PackageManager.PERMISSION_GRANTED) {
                startCaptureIntent();
            } else {
                toast("No Permission!");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveSelections();
        stopRecorder();
    }

    private void startCaptureIntent() {
        Intent captureIntent = mMediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION);
    }

    private void bindViews() {
        mButton = findViewById(R.id.record_button);
        mButton.setOnClickListener(this::onButtonClick);
        mAudioToggle = findViewById(R.id.with_audio);

/*
        mVideoCodec = findViewById(R.id.video_codec);
        mVieoResolution = findViewById(R.id.resolution);
        mVideoFramerate = findViewById(R.id.framerate);
        mIFrameInterval = findViewById(R.id.iframe_interval);
        mVideoBitrate = findViewById(R.id.video_bitrate);
        mOrientation = findViewById(R.id.orientation);

        mAudioCodec = findViewById(R.id.audio_codec);
        mVideoProfileLevel = findViewById(R.id.avc_profile);
        mAudioBitrate = findViewById(R.id.audio_bitrate);
        mAudioSampleRate = findViewById(R.id.sample_rate);
        mAudioProfile = findViewById(R.id.aac_profile);
        mAudioChannelCount = findViewById(R.id.audio_channel_count);

        mAudioToggle = findViewById(R.id.with_audio);
        mAudioToggle.setOnCheckedChangeListener((buttonView, isChecked) ->
                findViewById(R.id.audio_format_chooser)
                        .setVisibility(isChecked ? View.VISIBLE : View.GONE)
        );

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mOrientation.setSelectedPosition(1);
        }

        mVideoCodec.setOnItemSelectedListener((view, position) -> onVideoCodecSelected(view.getSelectedItem()));
        mAudioCodec.setOnItemSelectedListener((view, position) -> onAudioCodecSelected(view.getSelectedItem()));
        mVieoResolution.setOnItemSelectedListener((view, position) -> {
            if (position == 0) return;
            onResolutionChanged(position, view.getSelectedItem());
        });
        mVideoFramerate.setOnItemSelectedListener((view, position) -> {
            if (position == 0) return;
            onFramerateChanged(position, view.getSelectedItem());
        });
        mVideoBitrate.setOnItemSelectedListener((view, position) -> {
            if (position == 0) return;
            onBitrateChanged(position, view.getSelectedItem());
        });
        mOrientation.setOnItemSelectedListener((view, position) -> {
            if (position == 0) return;
            onOrientationChanged(position, view.getSelectedItem());
        });
*/
    }

    private void onButtonClick(View v) {
        if (mRecorder != null) {
            stopRecorder();
        } else if (hasPermissions()) {
            startCaptureIntent();
        } else if (Build.VERSION.SDK_INT >= M) {
            requestPermissions();
        } else {
            toast("No permission to write sd card");
        }
    }

    private void startRecorder() {
        if (mRecorder == null) return;
        Settings.Global.putInt(this.getContentResolver(), SCREEN_RECORDER_ON, 1);
        mRecorder.start();
        mButton.setText(R.string.stop_recording);
        registerReceiver(mStopActionReceiver, new IntentFilter(ACTION_STOP));
        moveTaskToBack(true);
    }

    private void stopRecorder() {
        mNotifications.clear();
        if (mRecorder != null) {
            mRecorder.quit();
        }
        mRecorder = null;
        Settings.Global.putInt(this.getContentResolver(), SCREEN_RECORDER_ON, 0);
        mButton.setText(R.string.start_recording);
        try {
            unregisterReceiver(mStopActionReceiver);
        } catch (Exception e) {
            //ignored
        }
    }

    private void cancelRecorder() {
        if (mRecorder == null) return;
        Toast.makeText(this, "Permission denied! Screen recorder is cancel", Toast.LENGTH_SHORT).show();
        stopRecorder();
    }

    @TargetApi(M)
    private void requestPermissions() {
        String[] permissions = mAudioToggle.isChecked()
                ? new String[]{WRITE_EXTERNAL_STORAGE, RECORD_AUDIO}
                : new String[]{WRITE_EXTERNAL_STORAGE};
        boolean showRationale = false;
        for (String perm : permissions) {
            showRationale |= shouldShowRequestPermissionRationale(perm);
        }
        if (!showRationale) {
            requestPermissions(permissions, REQUEST_PERMISSIONS);
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage("Using your mic to record audio and your sd card to save video file")
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        requestPermissions(permissions, REQUEST_PERMISSIONS))
                .setNegativeButton(android.R.string.cancel, null)
                .create()
                .show();
    }

    private boolean hasPermissions() {
        PackageManager pm = getPackageManager();
        String packageName = getPackageName();
        int granted = (mAudioToggle.isChecked() ? pm.checkPermission(RECORD_AUDIO, packageName) : PackageManager.PERMISSION_GRANTED)
                | pm.checkPermission(WRITE_EXTERNAL_STORAGE, packageName);
        return granted == PackageManager.PERMISSION_GRANTED;
    }

/*
    private void onResolutionChanged(int selectedPosition, String resolution) {
        String codecName = getSelectedVideoCodec();
        MediaCodecInfo codec = getVideoCodecInfo(codecName);
        if (codec == null) return;
        MediaCodecInfo.CodecCapabilities capabilities = codec.getCapabilitiesForType(VIDEO_AVC);
        MediaCodecInfo.VideoCapabilities videoCapabilities = capabilities.getVideoCapabilities();
        String[] xes = resolution.split("x");
        if (xes.length != 2) throw new IllegalArgumentException();
        boolean isLandscape = isLandscape();
        int width = Integer.parseInt(xes[isLandscape ? 0 : 1]);
        int height = Integer.parseInt(xes[isLandscape ? 1 : 0]);

        double selectedFramerate = getSelectedFramerate();
        int resetPos = Math.max(selectedPosition - 1, 0);
        if (!videoCapabilities.isSizeSupported(width, height)) {
            mVieoResolution.setSelectedPosition(resetPos);
            toast("codec '%s' unsupported size %dx%d (%s)",
                    codecName, width, height, mOrientation.getSelectedItem());
            Log.w("@@", codecName +
                    " height range: " + videoCapabilities.getSupportedHeights() +
                    "\n width range: " + videoCapabilities.getSupportedHeights());
        } else if (!videoCapabilities.areSizeAndRateSupported(width, height, selectedFramerate)) {
            mVieoResolution.setSelectedPosition(resetPos);
            toast("codec '%s' unsupported size %dx%d(%s)\nwith framerate %d",
                    codecName, width, height, mOrientation.getSelectedItem(), (int) selectedFramerate);
        }
    }

    private void onBitrateChanged(int selectedPosition, String bitrate) {
        String codecName = getSelectedVideoCodec();
        MediaCodecInfo codec = getVideoCodecInfo(codecName);
        if (codec == null) return;
        MediaCodecInfo.CodecCapabilities capabilities = codec.getCapabilitiesForType(VIDEO_AVC);
        MediaCodecInfo.VideoCapabilities videoCapabilities = capabilities.getVideoCapabilities();
        int selectedBitrate = Integer.parseInt(bitrate) * 1000;

        int resetPos = Math.max(selectedPosition - 1, 0);
        if (!videoCapabilities.getBitrateRange().contains(selectedBitrate)) {
            mVideoBitrate.setSelectedPosition(resetPos);
            toast("codec '%s' unsupported bitrate %d", codecName, selectedBitrate);
            Log.w("@@", codecName +
                    " bitrate range: " + videoCapabilities.getBitrateRange());
        }
    }

    private void onOrientationChanged(int selectedPosition, String orientation) {
        String codecName = getSelectedVideoCodec();
        MediaCodecInfo codec = getVideoCodecInfo(codecName);
        if (codec == null) return;
        MediaCodecInfo.CodecCapabilities capabilities = codec.getCapabilitiesForType(VIDEO_AVC);
        MediaCodecInfo.VideoCapabilities videoCapabilities = capabilities.getVideoCapabilities();
        int[] selectedWithHeight = getSelectedWithHeight();
        boolean isLandscape = selectedPosition == 1;
        int width = selectedWithHeight[isLandscape ? 0 : 1];
        int height = selectedWithHeight[isLandscape ? 1 : 0];
        int resetPos = Math.max(mVieoResolution.getSelectedItemPosition() - 1, 0);
        if (!videoCapabilities.isSizeSupported(width, height)) {
            mVieoResolution.setSelectedPosition(resetPos);
            toast("codec '%s' unsupported size %dx%d (%s)",
                    codecName, width, height, orientation);
            return;
        }

        int current = getResources().getConfiguration().orientation;
        if (isLandscape && current == Configuration.ORIENTATION_PORTRAIT) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else if (!isLandscape && current == Configuration.ORIENTATION_PORTRAIT) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    private void onFramerateChanged(int selectedPosition, String rate) {
        String codecName = getSelectedVideoCodec();
        MediaCodecInfo codec = getVideoCodecInfo(codecName);
        if (codec == null) return;
        MediaCodecInfo.CodecCapabilities capabilities = codec.getCapabilitiesForType(VIDEO_AVC);
        MediaCodecInfo.VideoCapabilities videoCapabilities = capabilities.getVideoCapabilities();
        int[] selectedWithHeight = getSelectedWithHeight();
        int selectedFramerate = Integer.parseInt(rate);
        boolean isLandscape = isLandscape();
        int width = selectedWithHeight[isLandscape ? 0 : 1];
        int height = selectedWithHeight[isLandscape ? 1 : 0];

        int resetPos = Math.max(selectedPosition - 1, 0);
        if (!videoCapabilities.getSupportedFrameRates().contains(selectedFramerate)) {
            mVideoFramerate.setSelectedPosition(resetPos);
            toast("codec '%s' unsupported framerate %d", codecName, selectedFramerate);
        } else if (!videoCapabilities.areSizeAndRateSupported(width, height, selectedFramerate)) {
            mVideoFramerate.setSelectedPosition(resetPos);
            toast("codec '%s' unsupported size %dx%d\nwith framerate %d",
                    codecName, width, height, selectedFramerate);
        }
    }

    private void onVideoCodecSelected(String codecName) {
        MediaCodecInfo codec = getVideoCodecInfo(codecName);
        if (codec == null) {
            mVideoProfileLevel.setAdapter(null);
            return;
        }
        MediaCodecInfo.CodecCapabilities capabilities = codec.getCapabilitiesForType(VIDEO_AVC);

        resetAvcProfileLevelAdapter(capabilities);
    }


    private void resetAvcProfileLevelAdapter(MediaCodecInfo.CodecCapabilities capabilities) {
        MediaCodecInfo.CodecProfileLevel[] profiles = capabilities.profileLevels;
        if (profiles == null || profiles.length == 0) {
            mVideoProfileLevel.setEnabled(false);
            return;
        }
        mVideoProfileLevel.setEnabled(true);
        String[] profileLevels = new String[profiles.length + 1];
        profileLevels[0] = "Default";
        for (int i = 0; i < profiles.length; i++) {
            profileLevels[i + 1] = Utils.avcProfileLevelToString(profiles[i]);
        }

        SpinnerAdapter old = mVideoProfileLevel.getAdapter();
        if (old == null || !(old instanceof ArrayAdapter)) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            adapter.addAll(profileLevels);
            mVideoProfileLevel.setAdapter(adapter);
        } else {
            ArrayAdapter<String> adapter = (ArrayAdapter<String>) old;
            adapter.setNotifyOnChange(false);
            adapter.clear();
            adapter.addAll(profileLevels);
            adapter.notifyDataSetChanged();
        }
    }

    private void onAudioCodecSelected(String codecName) {
        MediaCodecInfo codec = getAudioCodecInfo(codecName);
        if (codec == null) {
            mAudioProfile.setAdapter(null);
            mAudioSampleRate.setAdapter(null);
            mAudioBitrate.setAdapter(null);
            return;
        }
        MediaCodecInfo.CodecCapabilities capabilities = codec.getCapabilitiesForType(AUDIO_AAC);

        resetAudioBitrateAdapter(capabilities);
        resetSampleRateAdapter(capabilities);
        resetAacProfileAdapter(capabilities);
        restoreSelections(mAudioBitrate, mAudioSampleRate, mAudioProfile);
    }

    private void resetAacProfileAdapter(MediaCodecInfo.CodecCapabilities capabilities) {
        String[] profiles = Utils.aacProfiles();
        SpinnerAdapter old = mAudioProfile.getAdapter();
        if (old == null || !(old instanceof ArrayAdapter)) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            adapter.addAll(profiles);
            mAudioProfile.setAdapter(adapter);
        } else {
            ArrayAdapter<String> adapter = (ArrayAdapter<String>) old;
            adapter.setNotifyOnChange(false);
            adapter.clear();
            adapter.addAll(profiles);
            adapter.notifyDataSetChanged();
        }

    }

    private void resetSampleRateAdapter(MediaCodecInfo.CodecCapabilities capabilities) {
        int[] sampleRates = capabilities.getAudioCapabilities().getSupportedSampleRates();
        List<Integer> rates = new ArrayList<>(sampleRates.length);
        int preferred = -1;
        for (int i = 0; i < sampleRates.length; i++) {
            int sampleRate = sampleRates[i];
            if (sampleRate == 44100) {
                preferred = i;
            }
            rates.add(sampleRate);
        }

        SpinnerAdapter old = mAudioSampleRate.getAdapter();
        if (old == null || !(old instanceof ArrayAdapter)) {
            ArrayAdapter<Integer> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            adapter.addAll(rates);
            mAudioSampleRate.setAdapter(adapter);
        } else {
            ArrayAdapter<Integer> adapter = (ArrayAdapter<Integer>) old;
            adapter.setNotifyOnChange(false);
            adapter.clear();
            adapter.addAll(rates);
            adapter.notifyDataSetChanged();
        }
        mAudioSampleRate.setSelectedPosition(preferred);
    }

    private void resetAudioBitrateAdapter(MediaCodecInfo.CodecCapabilities capabilities) {
        Range<Integer> bitrateRange = capabilities.getAudioCapabilities().getBitrateRange();
        int lower = Math.max(bitrateRange.getLower() / 1000, 80);
        int upper = bitrateRange.getUpper() / 1000;
        List<Integer> rates = new ArrayList<>();
        for (int rate = lower; rate < upper; rate += lower) {
            rates.add(rate);
        }
        rates.add(upper);

        SpinnerAdapter old = mAudioBitrate.getAdapter();
        if (old == null || !(old instanceof ArrayAdapter)) {
            ArrayAdapter<Integer> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            adapter.addAll(rates);
            mAudioBitrate.setAdapter(adapter);
  /      } else {
            ArrayAdapter<Integer> adapter = (ArrayAdapter<Integer>) old;
            adapter.setNotifyOnChange(false);
            adapter.clear();
            adapter.addAll(rates);
            adapter.notifyDataSetChanged();
        }
        mAudioSampleRate.setSelectedPosition(rates.size() / 2);
    }
*/
    private MediaCodecInfo getVideoCodecInfo(String codecName) {
        if (codecName == null) return null;
        if (mAvcCodecInfos == null) {
            mAvcCodecInfos = Utils.findEncodersByType(VIDEO_AVC);
        }
        MediaCodecInfo codec = null;
        for (int i = 0; i < mAvcCodecInfos.length; i++) {
            MediaCodecInfo info = mAvcCodecInfos[i];
            if (info.getName().equals(codecName)) {
                codec = info;
                break;
            }
        }
        if (codec == null) return null;
        return codec;
    }

    private MediaCodecInfo getAudioCodecInfo(String codecName) {
        if (codecName == null) return null;
        if (mAacCodecInfos == null) {
            mAacCodecInfos = Utils.findEncodersByType(AUDIO_AAC);
        }
        MediaCodecInfo codec = null;
        for (int i = 0; i < mAacCodecInfos.length; i++) {
            MediaCodecInfo info = mAacCodecInfos[i];
            if (info.getName().equals(codecName)) {
                codec = info;
                break;
            }
        }
        if (codec == null) return null;
        return codec;
    }

/*
    private String getSelectedVideoCodec() {
        return mVideoCodec == null ? null : mVideoCodec.getSelectedItem();
    }

    private SpinnerAdapter createCodecsAdapter(MediaCodecInfo[] codecInfos) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, codecInfoNames(codecInfos));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private boolean isLandscape() {
        return mOrientation != null && mOrientation.getSelectedItemPosition() == 1;
    }

    private int getSelectedFramerate() {
        if (mVideoFramerate == null) throw new IllegalStateException();
        return Integer.parseInt(mVideoFramerate.getSelectedItem());
    }

    private int getSelectedVideoBitrate() {
        if (mVideoBitrate == null) throw new IllegalStateException();
        String selectedItem = mVideoBitrate.getSelectedItem(); //kbps
        return Integer.parseInt(selectedItem) * 1000;
    }

    private int getSelectedIFrameInterval() {
        return (mIFrameInterval != null) ? Integer.parseInt(mIFrameInterval.getSelectedItem()) : 5;
    }

    private MediaCodecInfo.CodecProfileLevel getSelectedProfileLevel() {
        return mVideoProfileLevel != null ? Utils.toProfileLevel(mVideoProfileLevel.getSelectedItem()) : null;
    }

    private int[] getSelectedWithHeight() {
        if (mVieoResolution == null) throw new IllegalStateException();
        String selected = mVieoResolution.getSelectedItem();
        String[] xes = selected.split("x");
        if (xes.length != 2) throw new IllegalArgumentException();
        return new int[]{Integer.parseInt(xes[0]), Integer.parseInt(xes[1])};

    }

    private String getSelectedAudioCodec() {
        return mAudioCodec == null ? null : mAudioCodec.getSelectedItem();
    }

    private int getSelectedAudioBitrate() {
        if (mAudioBitrate == null) throw new IllegalStateException();
        Integer selectedItem = mAudioBitrate.getSelectedItem();
        return selectedItem * 1000; // bps
    }

    private int getSelectedAudioSampleRate() {
        if (mAudioSampleRate == null) throw new IllegalStateException();
        Integer selectedItem = mAudioSampleRate.getSelectedItem();
        return selectedItem;
    }

    private int getSelectedAudioProfile() {
        if (mAudioProfile == null) throw new IllegalStateException();
        String selectedItem = mAudioProfile.getSelectedItem();
        MediaCodecInfo.CodecProfileLevel profileLevel = Utils.toProfileLevel(selectedItem);
        return profileLevel == null ? MediaCodecInfo.CodecProfileLevel.AACObjectMain : profileLevel.profile;
    }

    private int getSelectedAudioChannelCount() {
        if (mAudioChannelCount == null) throw new IllegalStateException();
        String selectedItem = mAudioChannelCount.getSelectedItem().toString();
        return Integer.parseInt(selectedItem);
    }
*/
    private void toast(String message, Object... args) {
        Toast toast = Toast.makeText(this,
                (args.length == 0) ? message : String.format(Locale.US, message, args),
                Toast.LENGTH_SHORT);
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(toast::show);
        } else {
            toast.show();
        }
    }

    private static String[] codecInfoNames(MediaCodecInfo[] codecInfos) {
        String[] names = new String[codecInfos.length];
        for (int i = 0; i < codecInfos.length; i++) {
            names[i] = codecInfos[i].getName();
        }
        return names;
    }

    /**
     * Print information of all MediaCodec on this device.
     */
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

/*
    private void restoreSelections(NamedSpinner... spinners) {
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        for (NamedSpinner spinner : spinners) {
            restoreSelectionFromPreferences(preferences, spinner);
        }
    }
*/
    private void saveSelections() {
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor edit = preferences.edit();
/*
        for (NamedSpinner spinner : new NamedSpinner[]{
                mVieoResolution,
                mVideoFramerate,
                mIFrameInterval,
                mVideoBitrate,
                mAudioBitrate,
                mAudioSampleRate,
                mAudioChannelCount,
                mVideoCodec,
                mAudioCodec,
                mAudioProfile,
        }) {
            saveSelectionToPreferences(edit, spinner);
        }
*/
        edit.putBoolean(getResources().getResourceEntryName(mAudioToggle.getId()), mAudioToggle.isChecked());
        edit.apply();
    }
/*
    private void saveSelectionToPreferences(SharedPreferences.Editor preferences, NamedSpinner spinner) {
        int resId = spinner.getId();
        String key = getResources().getResourceEntryName(resId);
        int selectedItemPosition = spinner.getSelectedItemPosition();
        if (selectedItemPosition >= 0) {
            preferences.putInt(key, selectedItemPosition);
        }
    }
    private void restoreSelectionFromPreferences(SharedPreferences preferences, NamedSpinner spinner) {
        int resId = spinner.getId();
        String key = getResources().getResourceEntryName(resId);
        int value = preferences.getInt(key, -1);
        if (value >= 0 && spinner.getAdapter() != null) {
            spinner.setSelectedPosition(value);
        }
    }
*/

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


    public void onBackPressed( ){

        if(android.util.FeatureOption.CF9_KS816 ){
            ;
        }else{
            super.onBackPressed();
        }
    }

}
