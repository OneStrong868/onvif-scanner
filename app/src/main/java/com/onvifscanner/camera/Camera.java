package com.onvifscanner.camera;

public class Camera {
    private String id;
    private String name;
    private String ipAddress;
    private int port;
    private String rtspUrl;
    private String username;
    private String password;
    private boolean isManual;
    private boolean isOnline;

    public Camera() {
        this.id = String.valueOf(System.currentTimeMillis());
        this.port = 554;
        this.isManual = false;
        this.isOnline = true;
    }

    public Camera(String name, String rtspUrl) {
        this();
        this.name = name;
        this.rtspUrl = rtspUrl;
        this.isManual = true;
    }

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
    
    public boolean isManual() { return isManual; }
    public void setManual(boolean manual) { this.isManual = manual; }
    
    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { this.isOnline = online; }
    
    public String getDisplayName() {
        if (name != null && !name.isEmpty()) return name;
        if (ipAddress != null) return ipAddress;
        return "Unknown Camera";
    }
    
    public String buildRtspUrl() {
        if (rtspUrl != null && !rtspUrl.isEmpty()) return rtspUrl;
        
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
}
