package com.onvifscanner;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
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

    private void setupPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        
        player.addListener(new android.media.session.MediaController.Callback() {
            // Simplified listener
        });
    }

    private void startStream() {
        progressBar.setVisibility(View.VISIBLE);
        tvError.setVisibility(View.GONE);

        String rtspUrl = buildRtspUrl();
        
        try {
            MediaItem mediaItem = new MediaItem.Builder()
                .setUri(Uri.parse(rtspUrl))
                .build();
            
            player.setMediaItem(mediaItem);
            player.prepare();
            player.setPlayWhenReady(true);
            
            player.addListener(new androidx.media3.common.Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    runOnUiThread(() -> {
                        if (state == ExoPlayer.STATE_READY) {
                            progressBar.setVisibility(View.GONE);
                        } else if (state == ExoPlayer.STATE_BUFFERING) {
                            progressBar.setVisibility(View.VISIBLE);
                        }
                    });
                }
                
                @Override
                public void onPlayerError(PlaybackException error) {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        tvError.setVisibility(View.VISIBLE);
                        tvError.setText("Connection failed: " + error.getMessage());
                    });
                }
            });
        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
            tvError.setVisibility(View.VISIBLE);
            tvError.setText("Error: " + e.getMessage());
        }
    }

    private String buildRtspUrl() {
        String url = camera.getRtspUrl();
        
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
