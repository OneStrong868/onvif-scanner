package com.onvifscanner.ui;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.onvifscanner.CameraDevice;
import com.onvifscanner.R;
import com.onvifscanner.network.OnvifDiscovery;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnvifDiscovery.DiscoveryListener {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private RecyclerView recyclerView;
    private CameraAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private List<CameraDevice> cameras = new ArrayList<>();
    private OnvifDiscovery discovery;
    private SharedPreferences prefs;
    private View emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        prefs = getSharedPreferences("cameras", MODE_PRIVATE);
        discovery = new OnvifDiscovery();
        discovery.setListener(this);

        initViews();
        checkPermissions();
        loadSavedCameras();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        emptyView = findViewById(R.id.emptyView);

        adapter = new CameraAdapter(cameras, this::onCameraClick, this::onCameraLongClick);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::startDiscovery);

        FloatingActionButton fab = findViewById(R.id.fabAdd);
        fab.setOnClickListener(v -> showAddCameraDialog());
    }

    private void checkPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES
            };
        } else {
            permissions = new String[]{
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION
            };
        }

        List<String> needed = new ArrayList<>();
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                needed.add(perm);
            }
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    private void loadSavedCameras() {
        try {
            String json = prefs.getString("camera_list", "[]");
            JSONArray arr = new JSONArray(json);
            cameras.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                CameraDevice cam = new CameraDevice();
                cam.setName(obj.optString("name", "Camera"));
                cam.setIpAddress(obj.optString("ip", ""));
                cam.setPort(obj.optInt("port", 80));
                cam.setRtspUrl(obj.optString("rtsp", ""));
                cam.setUsername(obj.optString("user", ""));
                cam.setPassword(obj.optString("pass", ""));
                cam.setManufacturer(obj.optString("mfr", ""));
                cam.setModel(obj.optString("model", ""));
                cameras.add(cam);
            }
            adapter.notifyDataSetChanged();
            updateEmptyView();
        } catch (Exception e) {
            Log.e(TAG, "Load cameras error", e);
        }
    }

    private void saveCameras() {
        try {
            JSONArray arr = new JSONArray();
            for (CameraDevice cam : cameras) {
                JSONObject obj = new JSONObject();
                obj.put("name", cam.getName());
                obj.put("ip", cam.getIpAddress());
                obj.put("port", cam.getPort());
                obj.put("rtsp", cam.getRtspUrl());
                obj.put("user", cam.getUsername());
                obj.put("pass", cam.getPassword());
                obj.put("mfr", cam.getManufacturer());
                obj.put("model", cam.getModel());
                arr.put(obj);
            }
            prefs.edit().putString("camera_list", arr.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Save cameras error", e);
        }
    }

    private void startDiscovery() {
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        int ip = wifi.getConnectionInfo().getIpAddress();
        
        if (ip == 0) {
            Snackbar.make(recyclerView, "Not connected to WiFi", Snackbar.LENGTH_LONG).show();
            swipeRefresh.setRefreshing(false);
            return;
        }

        String baseIp = String.format("%d.%d.%d.%d",
                ip & 0xff, (ip >> 8) & 0xff, (ip >> 16) & 0xff, (ip >> 24) & 0xff);
        
        swipeRefresh.setRefreshing(true);
        discovery.discoverCameras(baseIp, 24);
    }

    @Override
    public void onCameraFound(CameraDevice camera) {
        runOnUiThread(() -> {
            // Check if already in list
            for (CameraDevice c : cameras) {
                if (c.getIpAddress().equals(camera.getIpAddress())) {
                    return; // Already exists
                }
            }
            cameras.add(camera);
            adapter.notifyItemInserted(cameras.size() - 1);
            updateEmptyView();
            saveCameras();
        });
    }

    @Override
    public void onDiscoveryComplete(List<CameraDevice> foundCameras) {
        runOnUiThread(() -> {
            swipeRefresh.setRefreshing(false);
            Snackbar.make(recyclerView, 
                foundCameras.size() + " camera(s) found", 
                Snackbar.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onDiscoveryError(String error) {
        runOnUiThread(() -> {
            swipeRefresh.setRefreshing(false);
            Snackbar.make(recyclerView, "Error: " + error, Snackbar.LENGTH_LONG).show();
        });
    }

    private void showAddCameraDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Camera");

        View view = getLayoutInflater().inflate(R.layout.dialog_add_camera, null);
        EditText editName = view.findViewById(R.id.editName);
        EditText editIp = view.findViewById(R.id.editIp);
        EditText editPort = view.findViewById(R.id.editPort);
        EditText editRtsp = view.findViewById(R.id.editRtsp);
        EditText editUser = view.findViewById(R.id.editUser);
        EditText editPass = view.findViewById(R.id.editPass);

        builder.setView(view);
        builder.setPositiveButton("Add", (d, w) -> {
            CameraDevice cam = new CameraDevice();
            cam.setName(editName.getText().toString().trim());
            cam.setIpAddress(editIp.getText().toString().trim());
            
            String portStr = editPort.getText().toString().trim();
            cam.setPort(portStr.isEmpty() ? 554 : Integer.parseInt(portStr));
            
            cam.setRtspUrl(editRtsp.getText().toString().trim());
            cam.setUsername(editUser.getText().toString().trim());
            cam.setPassword(editPass.getText().toString().trim());
            
            if (cam.getRtspUrl().isEmpty()) {
                cam.setRtspUrl("rtsp://" + cam.getIpAddress() + ":554/stream1");
            }
            
            cameras.add(cam);
            adapter.notifyItemInserted(cameras.size() - 1);
            updateEmptyView();
            saveCameras();
            
            Toast.makeText(this, "Camera added", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void onCameraClick(CameraDevice camera, int position) {
        // Show credentials dialog if needed
        if (camera.getUsername() == null || camera.getUsername().isEmpty()) {
            showCredentialsDialog(camera, position);
        } else {
            openCameraView(camera);
        }
    }

    private void showCredentialsDialog(CameraDevice camera, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Credentials for " + camera.getName());

        View view = getLayoutInflater().inflate(R.layout.dialog_credentials, null);
        EditText editUser = view.findViewById(R.id.editUser);
        EditText editPass = view.findViewById(R.id.editPass);

        builder.setView(view);
        builder.setPositiveButton("Connect", (d, w) -> {
            camera.setUsername(editUser.getText().toString().trim());
            camera.setPassword(editPass.getText().toString().trim());
            saveCameras();
            openCameraView(camera);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void openCameraView(CameraDevice camera) {
        Intent intent = new Intent(this, CameraViewActivity.class);
        intent.putExtra("camera", camera);
        startActivity(intent);
    }

    private boolean onCameraLongClick(CameraDevice camera, int position) {
        new AlertDialog.Builder(this)
            .setTitle("Delete Camera?")
            .setMessage("Remove " + camera.getName() + " from list?")
            .setPositiveButton("Delete", (d, w) -> {
                cameras.remove(position);
                adapter.notifyItemRemoved(position);
                updateEmptyView();
                saveCameras();
            })
            .setNegativeButton("Cancel", null)
            .show();
        return true;
    }

    private void updateEmptyView() {
        emptyView.setVisibility(cameras.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_scan) {
            startDiscovery();
            return true;
        } else if (item.getItemId() == R.id.action_clear) {
            cameras.clear();
            adapter.notifyDataSetChanged();
            updateEmptyView();
            saveCameras();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (discovery != null) {
            discovery.shutdown();
        }
    }
}
