package com.onvifscanner.camera;

public class CameraDevice {
    private String name;
    private String ipAddress;
    private int port;
    private String rtspUrl;
    private String username;
    private String password;
    private String macAddress;
    private String manufacturer;
    private String model;
    private boolean isOnline;

    public CameraDevice() {
        this.port = 80;
        this.isOnline = true;
    }

    public CameraDevice(String name, String ipAddress, int port) {
        this.name = name;
        this.ipAddress = ipAddress;
        this.port = port;
        this.isOnline = true;
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getRtspUrl() { return rtspUrl; }
    public void setRtspUrl(String rtspUrl) { this.rtspUrl = rtspUrl; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }

    public String getFullRtspUrl() {
        if (rtspUrl != null && !rtspUrl.isEmpty()) {
            if (username != null && !username.isEmpty() && !rtspUrl.contains("@")) {
                // Add credentials to RTSP URL
                return rtspUrl.replace("rtsp://", "rtsp://" + username + ":" + password + "@");
            }
            return rtspUrl;
        }
        // Default RTSP URL format
        String creds = "";
        if (username != null && !username.isEmpty()) {
            creds = username + ":" + (password != null ? password : "") + "@";
        }
        return "rtsp://" + creds + ipAddress + ":" + port + "/stream1";
    }

    @Override
    public String toString() {
        return name + " (" + ipAddress + ")";
    }
}
