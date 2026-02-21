package com.onvifscanner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.onvifscanner.camera.CameraManager;
import com.onvifscanner.camera.OnvifCamera;
import com.onvifscanner.network.NetworkScanner;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements CameraAdapter.OnCameraClickListener {

    private static final int PERMISSION_REQUEST_CODE = 100;
    
    private RecyclerView recyclerView;
    private CameraAdapter adapter;
    private ProgressBar progressBar;
    private View emptyView;
    
    private CameraManager cameraManager;
    private NetworkScanner networkScanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraManager = new CameraManager(this);
        networkScanner = new NetworkScanner(this);

        initViews();
        checkPermissions();
        loadSavedCameras();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        emptyView = findViewById(R.id.emptyView);

        adapter = new CameraAdapter(new ArrayList<>(), this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        Button btnScan = findViewById(R.id.btnScan);
        btnScan.setOnClickListener(v -> startNetworkScan());

        Button btnAddManual = findViewById(R.id.btnAddManual);
        btnAddManual.setOnClickListener(v -> showAddCameraDialog());
    }

    private void checkPermissions() {
        String[] permissions = {
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_MULTICAST_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE
        };

        List<String> neededPermissions = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(permission);
            }
        }

        if (!neededPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, 
                neededPermissions.toArray(new String[0]), 
                PERMISSION_REQUEST_CODE);
        }
    }

    private void loadSavedCameras() {
        List<OnvifCamera> cameras = cameraManager.getCameras();
        updateCameraList(cameras);
    }

    private void startNetworkScan() {
        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);

        networkScanner.scanForOnvifCameras(new NetworkScanner.ScanCallback() {
            @Override
            public void onCameraFound(OnvifCamera camera) {
                runOnUiThread(() -> {
                    if (!cameraManager.cameraExists(camera)) {
                        cameraManager.addCamera(camera);
                        updateCameraList(cameraManager.getCameras());
                    }
                });
            }

            @Override
            public void onScanComplete(List<OnvifCamera> cameras) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (cameras.isEmpty()) {
                        emptyView.setVisibility(View.VISIBLE);
                        Toast.makeText(MainActivity.this, 
                            "No ONVIF cameras found", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, 
                            "Found " + cameras.size() + " camera(s)", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showAddCameraDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_camera, null);
        
        EditText etName = dialogView.findViewById(R.id.etCameraName);
        EditText etRtsp = dialogView.findViewById(R.id.etRtspUrl);
        EditText etUsername = dialogView.findViewById(R.id.etUsername);
        EditText etPassword = dialogView.findViewById(R.id.etPassword);

        builder.setView(dialogView)
            .setTitle("Add Camera Manually")
            .setPositiveButton("Add", (dialog, which) -> {
                String name = etName.getText().toString().trim();
                String rtsp = etRtsp.getText().toString().trim();
                String username = etUsername.getText().toString().trim();
                String password = etPassword.getText().toString().trim();

                if (rtsp.isEmpty()) {
                    Toast.makeText(this, "RTSP URL is required", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (name.isEmpty()) name = "Camera " + (cameraManager.getCameras().size() + 1);

                OnvifCamera camera = new OnvifCamera();
                camera.setName(name);
                camera.setRtspUrl(rtsp);
                camera.setUsername(username);
                camera.setPassword(password);
                camera.setManual(true);

                cameraManager.addCamera(camera);
                updateCameraList(cameraManager.getCameras());
                Toast.makeText(this, "Camera added", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void updateCameraList(List<OnvifCamera> cameras) {
        adapter.updateCameras(cameras);
        emptyView.setVisibility(cameras.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onCameraClick(OnvifCamera camera) {
        Intent intent = new Intent(this, CameraViewActivity.class);
        intent.putExtra("camera", camera);
        startActivity(intent);
    }

    @Override
    public void onCameraDelete(OnvifCamera camera) {
        new AlertDialog.Builder(this)
            .setTitle("Delete Camera")
            .setMessage("Remove " + camera.getName() + "?")
            .setPositiveButton("Delete", (dialog, which) -> {
                cameraManager.removeCamera(camera);
                updateCameraList(cameraManager.getCameras());
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_clear) {
            new AlertDialog.Builder(this)
                .setTitle("Clear All Cameras")
                .setMessage("Remove all saved cameras?")
                .setPositiveButton("Clear", (dialog, which) -> {
                    cameraManager.clearCameras();
                    updateCameraList(new ArrayList<>());
                })
                .setNegativeButton("Cancel", null)
                .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
