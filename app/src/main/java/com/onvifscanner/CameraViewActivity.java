package com.onvifscanner;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.ui.PlayerView;

import com.onvifscanner.camera.OnvifCamera;

public class CameraViewActivity extends AppCompatActivity {

    private ExoPlayer player;
    private PlayerView playerView;
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
        setupPlayer();
        startStream();
    }

    private void initViews() {
        playerView = findViewById(R.id.playerView);
        progressBar = findViewById(R.id.progressBar);
        tvError = findViewById(R.id.tvError);

        TextView tvTitle = findViewById(R.id.tvCameraName);
        tvTitle.setText(camera.getName());

        TextView tvUrl = findViewById(R.id.tvStreamUrl);
        tvUrl.setText(camera.getRtspUrl());

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
    }

    @OptIn(markerClass = UnstableApi.class)
    private void setupPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                runOnUiThread(() -> {
                    if (playbackState == Player.STATE_BUFFERING) {
                        progressBar.setVisibility(View.VISIBLE);
                        tvError.setVisibility(View.GONE);
                    } else if (playbackState == Player.STATE_READY) {
                        progressBar.setVisibility(View.GONE);
                        tvError.setVisibility(View.GONE);
                    } else if (playbackState == Player.STATE_ENDED) {
                        progressBar.setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvError.setVisibility(View.VISIBLE);
                    tvError.setText("Connection failed: " + getErrorMessage(error));
                });
            }
        });
    }

    @OptIn(markerClass = UnstableApi.class)
    private void startStream() {
        progressBar.setVisibility(View.VISIBLE);
        tvError.setVisibility(View.GONE);

        String rtspUrl = buildRtspUrl();
        
        try {
            RtspMediaSource.Factory rtspFactory = new RtspMediaSource.Factory();
            MediaItem mediaItem = new MediaItem.fromUri(rtspUrl);
            androidx.media3.common.MediaSource mediaSource = rtspFactory.createMediaSource(mediaItem);
            
            player.setMediaSource(mediaSource);
            player.prepare();
            player.playWhenReady = true;
        } catch (Exception e) {
            tvError.setVisibility(View.VISIBLE);
            tvError.setText("Failed to start stream: " + e.getMessage());
            progressBar.setVisibility(View.GONE);
        }
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

    private String getErrorMessage(PlaybackException error) {
        if (error == null) return "Unknown error";
        String msg = error.getMessage();
        if (msg == null) return "Connection failed";
        if (msg.contains("401")) return "Authentication failed. Check credentials.";
        if (msg.contains("timeout")) return "Connection timeout. Check network.";
        return msg.substring(0, Math.min(msg.length(), 50));
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            player.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
        }
    }
}
