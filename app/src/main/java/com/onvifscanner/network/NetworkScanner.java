package com.onvifscanner.network;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.onvifscanner.camera.OnvifCamera;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NetworkScanner {
    private static final String TAG = "NetworkScanner";
    private static final int WS_DISCOVERY_PORT = 3702;
    private static final String WS_DISCOVERY_MULTICAST = "239.255.255.250";
    
    private static final String WS_DISCOVERY_PROBE = 
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" " +
        "xmlns:a=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\">\n" +
        "<s:Header>\n" +
        "<a:Action s:mustUnderstand=\"1\">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</a:Action>\n" +
        "<a:MessageID>uuid:%s</a:MessageID>\n" +
        "<a:ReplyTo>\n" +
        "<a:Address>http://schemas.xmlsoap.org/ws/2004/08/addressing/role/anonymous</a:Address>\n" +
        "</a:ReplyTo>\n" +
        "<a:To s:mustUnderstand=\"1\">urn:schemas-xmlsoap-org:ws:2005/04:discovery</a:To>\n" +
        "</s:Header>\n" +
        "<s:Body>\n" +
        "<Probe xmlns=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\">\n" +
        "<d:Types xmlns:d=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\" " +
        "xmlns:dp0=\"http://www.onvif.org/ver10/network/wsdl\">dp0:NetworkVideoTransmitter</d:Types>\n" +
        "</Probe>\n" +
        "</s:Body>\n" +
        "</s:Envelope>";

    private final Context context;
    private final ExecutorService executor;
    private WifiManager.MulticastLock multicastLock;

    public NetworkScanner(Context context) {
        this.context = context;
        this.executor = Executors.newCachedThreadPool();
    }

    public interface ScanCallback {
        void onCameraFound(OnvifCamera camera);
        void onScanComplete(List<OnvifCamera> cameras);
        void onError(String error);
    }

    public void scanForOnvifCameras(ScanCallback callback) {
        executor.execute(() -> {
            List<OnvifCamera> foundCameras = new ArrayList<>();
            
            try {
                // Acquire multicast lock
                WifiManager wifi = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
                if (wifi != null) {
                    multicastLock = wifi.createMulticastLock("onvif_scanner");
                    multicastLock.acquire();
                }

                // Method 1: WS-Discovery
                List<OnvifCamera> wsCameras = wsDiscoveryScan();
                foundCameras.addAll(wsCameras);

                // Method 2: IP range scan
                List<OnvifCamera> ipCameras = ipRangeScan();
                for (OnvifCamera cam : ipCameras) {
                    boolean exists = false;
                    for (OnvifCamera existing : foundCameras) {
                        if (existing.getIpAddress() != null && 
                            existing.getIpAddress().equals(cam.getIpAddress())) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        foundCameras.add(cam);
                    }
                }

                // Release multicast lock
                if (multicastLock != null && multicastLock.isHeld()) {
                    multicastLock.release();
                }

                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onScanComplete(foundCameras));

            } catch (Exception e) {
                Log.e(TAG, "Scan error", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Scan failed: " + e.getMessage()));
            }
        });
    }

    private List<OnvifCamera> wsDiscoveryScan() throws Exception {
        List<OnvifCamera> cameras = new ArrayList<>();
        
        try (MulticastSocket socket = new MulticastSocket(WS_DISCOVERY_PORT)) {
            socket.setSoTimeout(5000);
            
            InetAddress group = InetAddress.getByName(WS_DISCOVERY_MULTICAST);
            socket.joinGroup(new InetSocketAddress(group, WS_DISCOVERY_PORT), null);

            // Send probe
            String probe = String.format(WS_DISCOVERY_PROBE, java.util.UUID.randomUUID().toString());
            byte[] probeData = probe.getBytes();
            DatagramPacket probePacket = new DatagramPacket(
                probeData, probeData.length, group, WS_DISCOVERY_PORT);
            socket.send(probePacket);

            // Listen for responses
            byte[] buffer = new byte[8192];
            long startTime = System.currentTimeMillis();
            
            while (System.currentTimeMillis() - startTime < 5000) {
                try {
                    DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                    socket.receive(response);
                    
                    String responseStr = new String(response.getData(), 0, response.getLength());
                    OnvifCamera camera = parseWsDiscoveryResponse(responseStr);
                    
                    if (camera != null) {
                        boolean exists = false;
                        for (OnvifCamera c : cameras) {
                            if (c.getIpAddress() != null && 
                                c.getIpAddress().equals(camera.getIpAddress())) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            cameras.add(camera);
                        }
                    }
                } catch (SocketTimeoutException e) {
                    break;
                }
            }
        }
        
        return cameras;
    }

    private OnvifCamera parseWsDiscoveryResponse(String response) {
        try {
            if (!response.contains("XAddrs") && !response.contains("onvif")) {
                return null;
            }

            String xaddr = extractValue(response, "<d:XAddrs>", "</d:XAddrs>");
            if (xaddr == null) {
                xaddr = extractValue(response, "<wsdd:XAddrs>", "</wsdd:XAddrs>");
            }
            if (xaddr == null) {
                xaddr = extractValue(response, "<XAddrs>", "</XAddrs>");
            }

            if (xaddr != null && xaddr.contains("http")) {
                OnvifCamera camera = new OnvifCamera();
                
                xaddr = xaddr.trim();
                if (xaddr.contains(" ")) {
                    xaddr = xaddr.split(" ")[0];
                }
                
                URL url = new URL(xaddr);
                camera.setIpAddress(url.getHost());
                camera.setPort(url.getPort() > 0 ? url.getPort() : 80);
                camera.setName("ONVIF Camera @ " + url.getHost());
                camera.setRtspUrl("rtsp://" + url.getHost() + ":554/stream1");
                
                return camera;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing WS-Discovery response", e);
        }
        return null;
    }

    private List<OnvifCamera> ipRangeScan() throws Exception {
        List<OnvifCamera> cameras = new ArrayList<>();
        
        String subnet = getSubnet();
        if (subnet == null) return cameras;

        ExecutorService scanExecutor = Executors.newFixedThreadPool(20);
        List<OnvifCamera> syncCameras = new ArrayList<>();
        
        for (int i = 1; i < 255; i++) {
            final String ip = subnet + "." + i;
            scanExecutor.execute(() -> {
                if (checkOnvifDevice(ip)) {
                    OnvifCamera camera = new OnvifCamera();
                    camera.setIpAddress(ip);
                    camera.setPort(80);
                    camera.setName("ONVIF Camera @ " + ip);
                    camera.setRtspUrl("rtsp://" + ip + ":554/stream1");
                    
                    synchronized (syncCameras) {
                        syncCameras.add(camera);
                    }
                }
            });
        }
        
        scanExecutor.shutdown();
        scanExecutor.awaitTermination(30, TimeUnit.SECONDS);
        
        cameras.addAll(syncCameras);
        return cameras;
    }

    private boolean checkOnvifDevice(String ip) {
        try {
            URL url = new URL("http://" + ip + "/onvif/device_service");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            conn.setRequestMethod("GET");
            
            int response = conn.getResponseCode();
            conn.disconnect();
            
            return response == 200 || response == 400 || response == 500;
        } catch (Exception e) {
            return false;
        }
    }

    private String getSubnet() {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
            int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
            
            return String.format("%d.%d.%d",
                (ipAddress & 0xff),
                (ipAddress >> 8 & 0xff),
                (ipAddress >> 16 & 0xff));
        } catch (Exception e) {
            return null;
        }
    }

    private String extractValue(String xml, String startTag, String endTag) {
        int start = xml.indexOf(startTag);
        if (start == -1) return null;
        start += startTag.length();
        int end = xml.indexOf(endTag, start);
        if (end == -1) return null;
        return xml.substring(start, end).trim();
    }
}
