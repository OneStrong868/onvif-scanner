package com.onvifscanner.camera;

public class CameraModel {
    private String id;
    private String name;
    private String ipAddress;
    private int port;
    private String rtspUrl;
    private String username;
    private String password;
    private boolean isDiscovered;
    
    public CameraModel() {
        this.isDiscovered = false;
        this.port = 554;
    }
    
    public CameraModel(String name, String ipAddress, int port, String rtspUrl) {
        this.name = name;
        this.ipAddress = ipAddress;
        this.port = port;
        this.rtspUrl = rtspUrl;
        this.isDiscovered = true;
        this.id = String.valueOf(System.currentTimeMillis());
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
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
    
    public boolean isDiscovered() { return isDiscovered; }
    public void setDiscovered(boolean discovered) { isDiscovered = discovered; }
    
    public String getFullRtspUrl() {
        if (rtspUrl != null && !rtspUrl.isEmpty()) {
            if (rtspUrl.startsWith("rtsp://")) {
                return rtspUrl;
            }
        }
        
        StringBuilder sb = new StringBuilder("rtsp://");
        if (username != null && !username.isEmpty()) {
            sb.append(username);
            if (password != null && !password.isEmpty()) {
                sb.append(":").append(password);
            }
            sb.append("@");
        }
        sb.append(ipAddress);
        if (port != 554) {
            sb.append(":").append(port);
        }
        sb.append("/stream1");
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return name + " (" + ipAddress + ")";
    }
}
