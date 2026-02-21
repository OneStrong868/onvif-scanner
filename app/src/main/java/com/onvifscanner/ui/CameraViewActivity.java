package com.onvifscanner.ui;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.onvifscanner.CameraDevice;
import com.onvifscanner.R;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import java.util.ArrayList;

public class CameraViewActivity extends AppCompatActivity implements IVLCVout.Callback {

    private static final String TAG = "CameraViewActivity";

    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    private Surface surface;
    private CameraDevice camera;
    private ProgressBar progressBar;
    private TextView txtStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_view);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        camera = (CameraDevice) getIntent().getSerializableExtra("camera");
        if (camera == null) {
            Toast.makeText(this, "No camera data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        progressBar = findViewById(R.id.progressBar);
        txtStatus = findViewById(R.id.txtStatus);
        surface = findViewById(R.id.surfaceView);

        TextView txtTitle = findViewById(R.id.txtTitle);
        txtTitle.setText(camera.getName());

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());

        initPlayer();
    }

    private void initPlayer() {
        ArrayList<String> options = new ArrayList<>();
        options.add("--network-caching=300");
        options.add("--rtsp-tcp");
        options.add("-vvv");

        libVLC = new LibVLC(this, options);
        mediaPlayer = new MediaPlayer(libVLC);

        IVLCVout vout = mediaPlayer.getVLCVout();
        vout.setVideoSurface(surface, null);
        vout.addCallback(this);
        vout.attachViews();

        startStream();
    }

    private void startStream() {
        String rtspUrl = camera.getFullRtspUrl();
        Log.d(TAG, "Playing: " + rtspUrl.replaceAll(":[^:@]+@", ":***@"));

        runOnUiThread(() -> {
            progressBar.setVisibility(View.VISIBLE);
            txtStatus.setText("Connecting...");
        });

        Media media = new Media(libVLC, Uri.parse(rtspUrl));
        media.setHWDecoderEnabled(true, true);
        media.addOption(":network-caching=300");
        media.addOption(":rtsp-tcp");

        mediaPlayer.setMedia(media);
        mediaPlayer.play();

        mediaPlayer.setEventListener(event -> {
            switch (event.type) {
                case MediaPlayer.Event.Opening:
                    runOnUiThread(() -> txtStatus.setText("Opening stream..."));
                    break;
                case MediaPlayer.Event.Playing:
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        txtStatus.setText("");
                    });
                    break;
                case MediaPlayer.Event.Buffering:
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.VISIBLE);
                        txtStatus.setText("Buffering: " + event.getBuffering() + "%");
                    });
                    break;
                case MediaPlayer.Event.EncounteredError:
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        txtStatus.setText("Connection failed");
                        Toast.makeText(this, "Failed to connect to camera", Toast.LENGTH_LONG).show();
                    });
                    break;
                case MediaPlayer.Event.TimeChanged:
                    runOnUiThread(() -> progressBar.setVisibility(View.GONE));
                    break;
            }
        });
    }

    @Override
    public void onSurfacesCreated(IVLCVout vout) {
        int width = vout.getWidth();
        int height = vout.getHeight();
        if (width > 0 && height > 0) {
            mediaPlayer.setAspectRatio(width + ":" + height);
            mediaPlayer.setScale(0);
        }
    }

    @Override
    public void onSurfacesDestroyed(IVLCVout vout) {
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null) {
            mediaPlayer.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.play();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }
        if (libVLC != null) {
            libVLC.release();
        }
    }
}
