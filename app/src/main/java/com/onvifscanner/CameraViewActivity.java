package com.onvifscanner;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.onvifscanner.camera.OnvifCamera;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.util.ArrayList;

public class CameraViewActivity extends AppCompatActivity {

    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    private VLCVideoLayout videoLayout;
    private ProgressBar progressBar;
    private TextView tvError;

    private OnvifCamera camera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_view);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        camera = (OnvifCamera) getIntent().getSerializableExtra("camera");

        if (camera == null) {
            finish();
            return;
        }

        initViews();
        setupVlc();
        startStream();
    }

    private void initViews() {
        videoLayout = findViewById(R.id.videoLayout);
        progressBar = findViewById(R.id.progressBar);
        tvError = findViewById(R.id.tvError);

        TextView tvTitle = findViewById(R.id.tvCameraName);
        tvTitle.setText(camera.getName());

        TextView tvUrl = findViewById(R.id.tvStreamUrl);
        tvUrl.setText(camera.getRtspUrl());

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
    }

    private void setupVlc() {
        ArrayList<String> options = new ArrayList<>();
        options.add("--network-caching=300");
        options.add("--rtsp-tcp");
        
        libVLC = new LibVLC(this, options);
        mediaPlayer = new MediaPlayer(libVLC);
        mediaPlayer.attachViews(videoLayout, null, false, false);
    }

    private void startStream() {
        progressBar.setVisibility(View.VISIBLE);
        tvError.setVisibility(View.GONE);

        String rtspUrl = buildRtspUrl();
        
        Media media = new Media(libVLC, rtspUrl);
        media.addOption(":network-caching=300");
        media.addOption(":rtsp-tcp");
        
        mediaPlayer.setMedia(media);
        
        mediaPlayer.setEventListener(event -> {
            switch (event.type) {
                case MediaPlayer.Event.Playing:
                    runOnUiThread(() -> progressBar.setVisibility(View.GONE));
                    break;
                case MediaPlayer.Event.EncounteredError:
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        tvError.setVisibility(View.VISIBLE);
                        tvError.setText("Connection failed. Check credentials and URL.");
                    });
                    break;
                case MediaPlayer.Event.Buffering:
                    runOnUiThread(() -> {
                        float buffering = event.getBuffering();
                        if (buffering < 100) {
                            progressBar.setVisibility(View.VISIBLE);
                        }
                    });
                    break;
            }
        });

        mediaPlayer.play();
    }

    private String buildRtspUrl() {
        String url = camera.getRtspUrl();
        
        // If credentials exist, inject them into RTSP URL
        if (!camera.getUsername().isEmpty() && !camera.getPassword().isEmpty()) {
            if (url.startsWith("rtsp://")) {
                url = "rtsp://" + camera.getUsername() + ":" + camera.getPassword() + 
                      "@" + url.substring(7);
            }
        }
        
        return url;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mediaPlayer != null) {
            mediaPlayer.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        if (libVLC != null) {
            libVLC.release();
        }
    }
}
