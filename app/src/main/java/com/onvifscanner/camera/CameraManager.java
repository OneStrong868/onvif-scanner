package com.onvifscanner.camera;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class CameraManager {
    private static final String PREFS_NAME = "onvif_scanner_prefs";
    private static final String KEY_CAMERAS = "cameras";

    private final SharedPreferences prefs;
    private final Gson gson;
    private List<OnvifCamera> cameras;

    public CameraManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        loadCameras();
    }

    private void loadCameras() {
        String json = prefs.getString(KEY_CAMERAS, null);
        if (json != null) {
            Type type = new TypeToken<List<OnvifCamera>>() {}.getType();
            cameras = gson.fromJson(json, type);
        } else {
            cameras = new ArrayList<>();
        }
    }

    private void saveCameras() {
        String json = gson.toJson(cameras);
        prefs.edit().putString(KEY_CAMERAS, json).apply();
    }

    public List<OnvifCamera> getCameras() {
        return new ArrayList<>(cameras);
    }

    public void addCamera(OnvifCamera camera) {
        if (!cameraExists(camera)) {
            cameras.add(camera);
            saveCameras();
        }
    }

    public void removeCamera(OnvifCamera camera) {
        cameras.remove(camera);
        saveCameras();
    }

    public void clearCameras() {
        cameras.clear();
        saveCameras();
    }

    public boolean cameraExists(OnvifCamera camera) {
        for (OnvifCamera c : cameras) {
            if (c.getRtspUrl() != null && c.getRtspUrl().equals(camera.getRtspUrl())) {
                return true;
            }
            if (c.getIpAddress() != null && c.getIpAddress().equals(camera.getIpAddress())) {
                return true;
            }
        }
        return false;
    }
}
