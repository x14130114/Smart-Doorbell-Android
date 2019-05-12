package com.example.bmc.spdoorbell;


import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.IOException;


/**
 *
 *  Video Streaming Activity
 *  Activity interacts with Firebase real time database getting stream HLS_URL and updates values.
 *  3 Functional features on the VideoStreaming activity:
 *  - View Amazon Kinesis Live Audio and Video stream on ExoPlayer view of the visitor from the Raspberry Pi Cam and Mic.
 *      + Waits until the HLS_URL is sent by the Raspberry Pi, uses handler to check for updated HLS_URL then starts video player.
 *  - Stop Live stream and return to Main Menu activity.
 *  - Press and hold button to record Audio message on Android phone microphone
 *      + Once released, uploads the recorded audio to Firebase storage
 *
 */

public class VideoStreaming extends AppCompatActivity {

    // Static string for logging tag value
    private static final String TAG = "HLS_URL SECTION!!";
    private PlayerView playerView;
    private ProgressBar loading;
    private boolean playWhenReady = true;
    private int currentWindow = 0;
    private long playbackPosition = 0;
    private ExoPlayer player;
    private String url = WAITING;

    private ImageButton mRecordBtn;
    private TextView mRecordLabel;
    private MediaRecorder recorder;
    private String fileName = null;
    private StorageReference mStorage;
    private ProgressDialog mProgress;
    private static final String WAITING = "Waiting";

    // Initializing Firebase instance and database reference
    DataSource.Factory dataSourceFactory;
    FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference data = database.getReference();

    /**
     * Handler used to monitor the HLS_URL and trigger ExoPlayer to start when the kinesis stream HLS_URL is available
     */
    public Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, String.format("Handler.handleMessage(): msg=%s", msg));
            startPlaying();
        }
    };

    /**
     * Runnable, checks the value of the URL every second and sends a message to the handler to start the video player
     */
    private final Runnable fetchHlsUrl = new Runnable(){
        public void run(){
            try {
                if(url.equalsIgnoreCase(WAITING) ){
                    handler.postDelayed(this, 1000);
                } else {
                    VideoStreaming.this.handler.sendEmptyMessage(1);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    /**
     *  onCreate function
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_streaming);

        // Initializing the player variables
        playerView = findViewById(R.id.video_view);
        loading = findViewById(R.id.loading);

        // Initializing Firebase storage instance and reference
        mStorage = FirebaseStorage.getInstance().getReference();

        // Initializing recording button and view
        mRecordLabel = findViewById(R.id.recordLabel);
        mRecordBtn =  findViewById(R.id.recordBtn);
        mProgress = new ProgressDialog(this);

        // Storing the recorded file locally to the phones absolute path
        fileName = Environment.getExternalStorageDirectory().getAbsolutePath();
        fileName += "/message1.mp3";

        // OnTouch listener for the record button - press and hold to record, when button released stop recording.
        mRecordBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                if(event.getAction() == MotionEvent.ACTION_DOWN){

                    startRecording();
                    mRecordLabel.setText("Recording Starting....");

                } else if (event.getAction() == MotionEvent.ACTION_UP){

                    stopRecording();
                    mRecordLabel.setText("Recording Stopped....");
                }

                return false;
            }
        });

        // onClick listener to stop the live stream, update the firebase value and go to the Main Menu activity
        Button stop = findViewById(R.id.stop);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                data.child("doorbell").child("streaming").child("stop_requested").setValue(1);
                data.child("doorbell").child("streaming").child("hls_url").setValue(WAITING);
                Intent myIntent = new Intent(VideoStreaming.this, MainActivity.class);
                VideoStreaming.this.startActivity(myIntent);
            }
        });
    }

    /**
     *  onStart function
     */
    @Override
    public void onStart() {
        super.onStart();

        // Firebase eventListener listens for changes to the firebase database
        data.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for(DataSnapshot ds : dataSnapshot.getChildren()){
                    // Set URL to URL from Firebase database
                    url = ds.child("streaming").child("hls_url").getValue().toString();
                    Log.d(TAG, "URL VALUE IS  "+url);
                    break;
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Cant read value
                Log.w(TAG, "Cant read value. ", error.toException());
            }
        });

        // Create default track selector and initialize the player
        TrackSelection.Factory adaptiveTrackSelection = new AdaptiveTrackSelection.Factory(new DefaultBandwidthMeter());
        player = ExoPlayerFactory.newSimpleInstance(
                new DefaultRenderersFactory(this),
                new DefaultTrackSelector(adaptiveTrackSelection),
                new DefaultLoadControl());

        // Initialize ExoPlayer view
        playerView.setPlayer(player);

        // Using defaultBandwidthMeter from ExoPlayer
        DefaultBandwidthMeter defaultBandwidthMeter = new DefaultBandwidthMeter();
        // Produces DataSource instances through which media data is loaded.
        dataSourceFactory = new DefaultDataSourceFactory(this,
                Util.getUserAgent(this, "Exo2"), defaultBandwidthMeter);
    }

    /**
     *  onResume function
     */
    @Override
    public void onResume(){
        super.onResume();

        // Tell the handler to run the fetchHlsUrl runnable to start watching the URL and trigger video playback
        handler.postDelayed(fetchHlsUrl, 1000);

    }

    // Method to release the ExoPlayer
    private void releasePlayer() {
        if (player != null) {
            playbackPosition = player.getCurrentPosition();
            currentWindow = player.getCurrentWindowIndex();
            playWhenReady = player.getPlayWhenReady();
            player.release();
            player = null;
        }
    }

    /**
     *  onStop function
     */
    public void onStop(){
        super.onStop();
        if (Util.SDK_INT > 23) {
            releasePlayer();
        }
    }

    /**
     *  onPause function
     */
    public void onPause(){
        super.onPause();
        if (Util.SDK_INT <= 23) {
            releasePlayer();
        }
    }

    // Method to start recording audio from the phone microphone
    private void startRecording() {
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
        recorder.setOutputFile(fileName);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            recorder.prepare();
        } catch (IOException e) {
            Log.e(TAG, "prepare() failed");
        }

        recorder.start();
    }

    // Method to stop recording and start the upload method
    private void stopRecording() {
        recorder.stop();
        recorder.release();
        recorder = null;

        uploadAudio();
    }

    // Upload method to upload the recorded audio to Firebase storage
    private void uploadAudio() {
        mProgress.setMessage("Uploading Audio");
        mProgress.show();
        StorageReference filepath = mStorage.child("Audio").child("visitor_voice.mp3");
        // Create file metadata including the content type
        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType("audio/mpeg")
                .build();

        Uri uri = Uri.fromFile(new File(fileName));

        //If the upload is successful then print uploading finished and update the firebase audio state value to "new"
        filepath.putFile(uri, metadata).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                mProgress.dismiss();
                mRecordLabel.setText("Uploading Finished");
                data.child("doorbell").child("audio").child("state").setValue("new");
            }
        });
    }

    // Method to start playing the Live video in the ExoPlayer view
    private void startPlaying(){

        // Parsing the HLS_URL
        Uri uri = Uri.parse(url);
        Handler mainHandler = new Handler();

        // Using HLSMediaSource to format stream on ExoPlayer
        MediaSource mediaSource = new HlsMediaSource(uri,
                dataSourceFactory, mainHandler, null);
        player.prepare(mediaSource);

        // Play when HLS_URL is active and retrieved
        player.setPlayWhenReady(playWhenReady);
        // EventListener for the video player
        player.addListener(new Player.EventListener() {
            @Override
            public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {

            }

            @Override
            public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

            }

            @Override
            public void onLoadingChanged(boolean isLoading) {

            }

            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                switch (playbackState) {
                    case ExoPlayer.STATE_READY:
                        loading.setVisibility(View.GONE);
                        break;
                    case ExoPlayer.STATE_BUFFERING:
                        loading.setVisibility(View.VISIBLE);
                        break;
                }
            }

            @Override
            public void onRepeatModeChanged(int repeatMode) {

            }

            @Override
            public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {

            }

            @Override
            public void onPlayerError(ExoPlaybackException error) {

            }

            @Override
            public void onPositionDiscontinuity(int reason) {

            }

            @Override
            public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

            }

            @Override
            public void onSeekProcessed() {

            }
        });
        player.seekTo(currentWindow, playbackPosition);
        player.prepare(mediaSource, true, false);
    }

}
