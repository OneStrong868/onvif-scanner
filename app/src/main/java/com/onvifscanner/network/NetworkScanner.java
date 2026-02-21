package com.onvifscanner.network;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.onvifscanner.camera.OnvifCamera;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class NetworkScanner {
    private static final String TAG = "NetworkScanner";
    private static final int ONVIF_PORT = 80;
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
        "<a:To s:mustUnderstand=\"1\">urn:schemas-xmlsoap-org:ws:2005:04:discovery</a:To>\n" +
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
                    if (!foundCameras.contains(cam)) {
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
            socket.joinGroup(group);

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
                    
                    if (camera != null && !cameras.contains(camera)) {
                        cameras.add(camera);
                    }
                } catch (SocketTimeoutException e) {
                    break;
                }
            }
            
            socket.leaveGroup(group);
        }
        
        return cameras;
    }

    private OnvifCamera parseWsDiscoveryResponse(String response) {
        try {
            // Look for XAddrs (device service URL)
            if (!response.contains("XAddrs") && !response.contains("onvif")) {
                return null;
            }

            // Extract XAddr
            String xaddr = extractValue(response, "<d:XAddrs>", "</d:XAddrs>");
            if (xaddr == null) {
                xaddr = extractValue(response, "<wsdd:XAddrs>", "</wsdd:XAddrs>");
            }

            if (xaddr != null && xaddr.contains("onvif")) {
                OnvifCamera camera = new OnvifCamera();
                
                // Parse URL
                URL url = new URL(xaddr.trim());
                camera.setIpAddress(url.getHost());
                camera.setPort(url.getPort() > 0 ? url.getPort() : 80);
                camera.setName("ONVIF Camera @ " + url.getHost());
                
                // Try to get RTSP URL
                String rtspUrl = getRtspUrl(xaddr.trim());
                if (rtspUrl != null) {
                    camera.setRtspUrl(rtspUrl);
                } else {
                    // Default RTSP path
                    camera.setRtspUrl("rtsp://" + url.getHost() + ":554/stream1");
                }
                
                return camera;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing WS-Discovery response", e);
        }
        return null;
    }

    private List<OnvifCamera> ipRangeScan() throws Exception {
        List<OnvifCamera> cameras = new ArrayList<>();
        
        // Get current subnet
        String subnet = getSubnet();
        if (subnet == null) return cameras;

        // Scan common camera ports on subnet
        ExecutorService scanExecutor = Executors.newFixedThreadPool(20);
        
        for (int i = 1; i < 255; i++) {
            final String ip = subnet + "." + i;
            scanExecutor.execute(() -> {
                if (checkOnvifDevice(ip)) {
                    OnvifCamera camera = new OnvifCamera();
                    camera.setIpAddress(ip);
                    camera.setPort(80);
                    camera.setName("ONVIF Camera @ " + ip);
                    camera.setRtspUrl("rtsp://" + ip + ":554/stream1");
                    
                    synchronized (cameras) {
                        cameras.add(camera);
                    }
                }
            });
        }
        
        scanExecutor.shutdown();
        scanExecutor.awaitTermination(30, TimeUnit.SECONDS);
        
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

    private String getRtspUrl(String deviceServiceUrl) {
        try {
            // ONVIF GetStreamUri request
            String soapRequest = 
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\"\n" +
                "xmlns:trt=\"http://www.onvif.org/ver10/media/wsdl\">\n" +
                "<s:Body>\n" +
                "<trt:GetStreamUri>\n" +
                "<trt:StreamSetup>\n" +
                "<tt:Stream xmlns:tt=\"http://www.onvif.org/ver10/schema\">RTP-Unicast</tt:Stream>\n" +
                "<tt:Transport xmlns:tt=\"http://www.onvif.org/ver10/schema\">\n" +
                "<tt:Protocol>RTSP</tt:Protocol>\n" +
                "</tt:Transport>\n" +
                "</trt:StreamSetup>\n" +
                "<trt:ProfileToken>profile_1</trt:ProfileToken>\n" +
                "</trt:GetStreamUri>\n" +
                "</s:Body>\n" +
                "</s:Envelope>";

            URL url = new URL(deviceServiceUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/soap+xml");
            conn.setDoOutput(true);
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            OutputStream os = conn.getOutputStream();
            os.write(soapRequest.getBytes());
            os.flush();

            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                br.close();
                
                // Extract RTSP URI
                String rtsp = extractValue(response.toString(), "<tt:Uri>", "</tt:Uri>");
                return rtsp;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting RTSP URL", e);
        }
        return null;
    }

    private String getSubnet() {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
            int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
            
            String ipString = String.format("%d.%d.%d",
                (ipAddress & 0xff),
                (ipAddress >> 8 & 0xff),
                (ipAddress >> 16 & 0xff));
            
            return ipString;
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
