package com.onvifscanner.camera;

import java.io.Serializable;

public class CameraInfo implements Serializable {
    private String name;
    private String ip;
    private int port;
    private String rtspUrl;
    private String username;
    private String password;
    private String manufacturer;
    private String model;
    private boolean isOnvif;
    private boolean isSaved;

    public CameraInfo() {}

    public CameraInfo(String name, String rtspUrl) {
        this.name = name;
        this.rtspUrl = rtspUrl;
        this.isOnvif = false;
    }

    public CameraInfo(String ip, int port) {
        this.ip = ip;
        this.port = port;
        this.isOnvif = true;
    }

    // Getters
    public String getName() { return name; }
    public String getIp() { return ip; }
    public int getPort() { return port; }
    public String getRtspUrl() { return rtspUrl; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getManufacturer() { return manufacturer; }
    public String getModel() { return model; }
    public boolean isOnvif() { return isOnvif; }
    public boolean isSaved() { return isSaved; }

    // Setters
    public void setName(String name) { this.name = name; }
    public void setIp(String ip) { this.ip = ip; }
    public void setPort(int port) { this.port = port; }
    public void setRtspUrl(String rtspUrl) { this.rtspUrl = rtspUrl; }
    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    public void setModel(String model) { this.model = model; }
    public void setOnvif(boolean onvif) { isOnvif = onvif; }
    public void setSaved(boolean saved) { isSaved = saved; }

    // Build RTSP URL
    public String buildRtspUrl() {
        if (rtspUrl != null && !rtspUrl.isEmpty()) {
            return rtspUrl;
        }
        
        StringBuilder sb = new StringBuilder("rtsp://");
        if (username != null && !username.isEmpty()) {
            sb.append(username);
            if (password != null && !password.isEmpty()) {
                sb.append(":").append(password);
            }
            sb.append("@");
        }
        sb.append(ip);
        if (port > 0 && port != 554) {
            sb.append(":").append(port);
        }
        sb.append("/stream1");
        
        return sb.toString();
    }

    @Override
    public String toString() {
        return name + " (" + ip + ")";
    }
}
