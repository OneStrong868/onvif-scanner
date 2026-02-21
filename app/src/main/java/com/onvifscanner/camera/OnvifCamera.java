package com.onvifscanner.camera;

import java.io.Serializable;

public class OnvifCamera implements Serializable {
    private String id;
    private String name;
    private String ipAddress;
    private int port;
    private String rtspUrl;
    private String username;
    private String password;
    private String model;
    private String manufacturer;
    private boolean isManual;

    public OnvifCamera() {
        this.id = String.valueOf(System.currentTimeMillis());
        this.port = 80;
        this.username = "";
        this.password = "";
        this.isManual = false;
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

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public boolean isManual() { return isManual; }
    public void setManual(boolean manual) { isManual = manual; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OnvifCamera that = (OnvifCamera) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
