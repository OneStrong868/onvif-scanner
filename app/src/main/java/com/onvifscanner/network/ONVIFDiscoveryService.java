package com.onvifscanner.network;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.onvifscanner.camera.Camera;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ONVIFDiscoveryService {
    private static final String TAG = "ONVIFDiscovery";
    private static final String MULTICAST_ADDRESS = "239.255.255.250";
    private static final int MULTICAST_PORT = 3702;
    private static final int TIMEOUT_MS = 5000;

    private static final String PROBE_MESSAGE = 
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" " +
        "xmlns:a=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\">\n" +
        "<s:Header>\n" +
        "<a:Action s:mustUnderstand=\"1\">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</a:Action>\n" +
        "<a:MessageID>uuid:dage648a-4567-89ab-cdef-123456789abc</a:MessageID>\n" +
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

    public interface DiscoveryCallback {
        void onCameraFound(Camera camera);
        void onDiscoveryComplete(List<Camera> cameras);
        void onError(String error);
    }

    private Context context;
    private WifiManager.MulticastLock multicastLock;
    private volatile boolean isRunning = false;

    public ONVIFDiscoveryService(Context context) {
        this.context = context;
    }

    public void startDiscovery(DiscoveryCallback callback) {
        new Thread(() -> {
            List<Camera> cameras = new ArrayList<>();
            isRunning = true;
            
            WifiManager wifi = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                multicastLock = wifi.createMulticastLock("onvif_discovery");
                multicastLock.setReferenceCounted(true);
                multicastLock.acquire();
            }

            try (MulticastSocket socket = new MulticastSocket(null)) {
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(MULTICAST_PORT));
                socket.setSoTimeout(TIMEOUT_MS);

                InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
                socket.joinGroup(group);

                // Send probe
                byte[] probeData = PROBE_MESSAGE.getBytes();
                DatagramPacket probePacket = new DatagramPacket(
                        probeData, probeData.length, group, MULTICAST_PORT);
                socket.send(probePacket);

                Log.d(TAG, "Sent ONVIF probe message");

                // Listen for responses
                byte[] buffer = new byte[8192];
                long startTime = System.currentTimeMillis();

                while (isRunning && (System.currentTimeMillis() - startTime) < TIMEOUT_MS * 2) {
                    try {
                        DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
                        socket.receive(responsePacket);

                        String response = new String(responsePacket.getData(), 0, responsePacket.getLength());
                        Log.d(TAG, "Received response from " + responsePacket.getAddress());

                        Camera camera = parseResponse(response, responsePacket.getAddress().getHostAddress());
                        if (camera != null && !containsCamera(cameras, camera)) {
                            cameras.add(camera);
                            if (callback != null) {
                                callback.onCameraFound(camera);
                            }
                        }
                    } catch (SocketTimeoutException e) {
                        // Continue waiting
                    }
                }

                socket.leaveGroup(group);

            } catch (IOException e) {
                Log.e(TAG, "Discovery error: " + e.getMessage());
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            } finally {
                if (multicastLock != null && multicastLock.isHeld()) {
                    multicastLock.release();
                }
                if (callback != null) {
                    callback.onDiscoveryComplete(cameras);
                }
            }
        }).start();
    }

    public void stopDiscovery() {
        isRunning = false;
    }

    private Camera parseResponse(String response, String ipAddress) {
        if (response.contains("NetworkVideoTransmitter") || 
            response.contains("onvif") || response.contains("ONVIF")) {
            
            Camera camera = new Camera();
            camera.setIpAddress(ipAddress);
            camera.setOnline(true);
            camera.setManual(false);

            // Try to extract device name
            Pattern namePattern = Pattern.compile("<.*?Name[^>]*>([^<]+)<");
            Matcher nameMatcher = namePattern.matcher(response);
            if (nameMatcher.find()) {
                camera.setName(nameMatcher.group(1));
            } else {
                camera.setName("ONVIF Camera");
            }

            // Try to extract XAddrs for service URL
            Pattern urlPattern = Pattern.compile("http[s]?://([^:/]+):(\\d+)[^<]*");
            Matcher urlMatcher = urlPattern.matcher(response);
            if (urlMatcher.find()) {
                try {
                    camera.setPort(Integer.parseInt(urlMatcher.group(2)));
                } catch (NumberFormatException e) {
                    camera.setPort(80);
                }
            }

            // Build RTSP URL
            camera.setRtspUrl("rtsp://" + ipAddress + ":554/stream1");

            return camera;
        }
        return null;
    }

    private boolean containsCamera(List<Camera> cameras, Camera camera) {
        for (Camera c : cameras) {
            if (c.getIpAddress().equals(camera.getIpAddress())) {
                return true;
            }
        }
        return false;
    }
}
