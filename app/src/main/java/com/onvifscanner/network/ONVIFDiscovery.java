package com.onvifscanner.network;

import android.net.wifi.WifiManager;
import android.util.Log;

import com.onvifscanner.camera.CameraModel;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class ONVIFDiscovery {
    private static final String TAG = "ONVIFDiscovery";
    private static final int ONVIF_PORT = 3702;
    private static final int RTSP_PORT = 554;
    private static final int SCAN_TIMEOUT = 2000;
    
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private DiscoveryCallback callback;
    
    public interface DiscoveryCallback {
        void onCameraFound(CameraModel camera);
        void onScanComplete(List<CameraModel> cameras);
        void onScanError(String error);
        void onProgress(String message);
    }
    
    public void setCallback(DiscoveryCallback callback) {
        this.callback = callback;
    }
    
    public void startDiscovery(String subnet) {
        executor.execute(() -> {
            List<CameraModel> foundCameras = new ArrayList<>();
            
            try {
                callback.onProgress("Starting network scan...");
                
                // Parse subnet (e.g., "192.168.1" from "192.168.1.100")
                String[] parts = subnet.split("\\.");
                String baseIp = parts[0] + "." + parts[1] + "." + parts[2];
                
                callback.onProgress("Scanning " + baseIp + ".0/24...");
                
                // Create thread pool for parallel scanning
                ExecutorService scanExecutor = Executors.newFixedThreadPool(50);
                
                for (int i = 1; i < 255; i++) {
                    final String ip = baseIp + "." + i;
                    scanExecutor.execute(() -> {
                        if (checkONVIFPort(ip)) {
                            CameraModel camera = probeCamera(ip);
                            if (camera != null) {
                                foundCameras.add(camera);
                                if (callback != null) {
                                    callback.onCameraFound(camera);
                                }
                            }
                        }
                    });
                }
                
                scanExecutor.shutdown();
                scanExecutor.awaitTermination(30, TimeUnit.SECONDS);
                
                if (callback != null) {
                    callback.onScanComplete(foundCameras);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Discovery error", e);
                if (callback != null) {
                    callback.onScanError(e.getMessage());
                }
            }
        });
    }
    
    private boolean checkONVIFPort(String ip) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, ONVIF_PORT), SCAN_TIMEOUT);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private CameraModel probeCamera(String ip) {
        try {
            // Try to get device info via ONVIF WS-Discovery
            String probeMessage = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" " +
                "xmlns:a=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\">\n" +
                "<s:Header>\n" +
                "<a:Action s:mustUnderstand=\"1\">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</a:Action>\n" +
                "<a:MessageID>uuid:" + java.util.UUID.randomUUID() + "</a:MessageID>\n" +
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
            
            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(SCAN_TIMEOUT);
            
            byte[] data = probeMessage.getBytes();
            DatagramPacket packet = new DatagramPacket(
                data, data.length,
                InetAddress.getByName(ip), ONVIF_PORT
            );
            
            socket.send(packet);
            
            byte[] buffer = new byte[4096];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);
            
            String responseStr = new String(response.getData(), 0, response.getLength());
            
            // Parse camera name and RTSP URL from response
            String cameraName = extractFromXML(responseStr, "d:XAddrs");
            if (cameraName == null) {
                cameraName = "ONVIF Camera";
            }
            
            // Try to construct RTSP URL
            String rtspUrl = "rtsp://" + ip + ":554/stream1";
            
            CameraModel camera = new CameraModel(cameraName, ip, RTSP_PORT, rtspUrl);
            socket.close();
            
            return camera;
            
        } catch (Exception e) {
            Log.e(TAG, "Probe error for " + ip, e);
            return null;
        }
    }
    
    private String extractFromXML(String xml, String tag) {
        try {
            Pattern pattern = Pattern.compile("<" + tag + "[^>]*>([^<]+)</" + tag + ">");
            Matcher matcher = pattern.matcher(xml);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            Log.e(TAG, "XML parse error", e);
        }
        return null;
    }
    
    public void shutdown() {
        executor.shutdown();
    }
}
