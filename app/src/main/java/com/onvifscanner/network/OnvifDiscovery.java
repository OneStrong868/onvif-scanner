package com.onvifscanner.network;

import android.util.Log;

import com.onvifscanner.CameraDevice;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
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
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class OnvifDiscovery {
    private static final String TAG = "OnvifDiscovery";
    private static final int ONVIF_PORT = 3702;
    private static final int TIMEOUT_MS = 3000;
    private static final int THREAD_POOL_SIZE = 50;

    private final ExecutorService executor;
    private DiscoveryListener listener;

    public interface DiscoveryListener {
        void onCameraFound(CameraDevice camera);
        void onDiscoveryComplete(List<CameraDevice> cameras);
        void onDiscoveryError(String error);
    }

    public OnvifDiscovery() {
        this.executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    }

    public void setListener(DiscoveryListener listener) {
        this.listener = listener;
    }

    public void discoverCameras(String baseIp, int subnetMask) {
        executor.submit(() -> {
            List<CameraDevice> cameras = new ArrayList<>();
            List<Future<?>> futures = new ArrayList<>();

            // Calculate IP range
            String[] parts = baseIp.split("\\.");
            String prefix = parts[0] + "." + parts[1] + "." + parts[2] + ".";

            // Probe WS-Discovery
            probeWSDiscovery(cameras);

            // Scan each IP in range
            for (int i = 1; i < 255; i++) {
                final String ip = prefix + i;
                futures.add(executor.submit(() -> {
                    CameraDevice camera = probeOnvif(ip);
                    if (camera != null) {
                        synchronized (cameras) {
                            // Check if not already found
                            if (!containsIp(cameras, ip)) {
                                cameras.add(camera);
                                if (listener != null) {
                                    listener.onCameraFound(camera);
                                }
                            }
                        }
                    }
                }));
            }

            // Wait for all to complete
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    Log.e(TAG, "Scan error", e);
                }
            }

            if (listener != null) {
                listener.onDiscoveryComplete(cameras);
            }
        });
    }

    private boolean containsIp(List<CameraDevice> cameras, String ip) {
        for (CameraDevice c : cameras) {
            if (c.getIpAddress().equals(ip)) return true;
        }
        return false;
    }

    private void probeWSDiscovery(List<CameraDevice> cameras) {
        try {
            String probeMessage = buildProbeMessage();
            byte[] sendData = probeMessage.getBytes();

            MulticastSocket socket = new MulticastSocket(ONVIF_PORT);
            socket.setSoTimeout(TIMEOUT_MS);
            socket.setTimeToLive(1);

            InetAddress group = InetAddress.getByName("239.255.255.250");
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, group, ONVIF_PORT);
            socket.send(sendPacket);

            byte[] receiveData = new byte[4096];
            long startTime = System.currentTimeMillis();

            while (System.currentTimeMillis() - startTime < TIMEOUT_MS) {
                try {
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    socket.receive(receivePacket);

                    String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    CameraDevice camera = parseWSDiscoveryResponse(response);

                    if (camera != null && !containsIp(cameras, camera.getIpAddress())) {
                        cameras.add(camera);
                        if (listener != null) {
                            listener.onCameraFound(camera);
                        }
                    }
                } catch (SocketTimeoutException e) {
                    break;
                }
            }
            socket.close();
        } catch (Exception e) {
            Log.e(TAG, "WS-Discovery error", e);
        }
    }

    private String buildProbeMessage() {
        String uuid = UUID.randomUUID().toString();
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" " +
                "xmlns:a=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\">\n" +
                "<s:Header>\n" +
                "<a:Action s:mustUnderstand=\"1\">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</a:Action>\n" +
                "<a:MessageID>uuid:" + uuid + "</a:MessageID>\n" +
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
    }

    private CameraDevice parseWSDiscoveryResponse(String response) {
        try {
            // Extract XAddrs (device service URL)
            Pattern addrPattern = Pattern.compile("XAddrs>([^<]+)<");
            Matcher addrMatcher = addrPattern.matcher(response);
            
            if (addrMatcher.find()) {
                String xaddrs = addrMatcher.group(1).trim();
                String[] urls = xaddrs.split("\\s+");
                
                if (urls.length > 0) {
                    URL url = new URL(urls[0]);
                    CameraDevice camera = new CameraDevice();
                    camera.setIpAddress(url.getHost());
                    camera.setPort(url.getPort() > 0 ? url.getPort() : 80);
                    
                    // Try to extract device name
                    Pattern namePattern = Pattern.compile("d:Types[^>]*>([^<]+)<");
                    Matcher nameMatcher = namePattern.matcher(response);
                    if (nameMatcher.find()) {
                        camera.setName("ONVIF Camera");
                    } else {
                        camera.setName("Camera " + url.getHost());
                    }
                    
                    return camera;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Parse WS-Discovery error", e);
        }
        return null;
    }

    private CameraDevice probeOnvif(String ip) {
        try {
            // Try common ONVIF ports
            int[] ports = {80, 8080, 8000, 8999};
            
            for (int port : ports) {
                try {
                    String url = "http://" + ip + ":" + port + "/onvif/device_service";
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(500);
                    conn.setReadTimeout(500);
                    
                    int responseCode = conn.getResponseCode();
                    conn.disconnect();
                    
                    if (responseCode == 200 || responseCode == 400 || responseCode == 500) {
                        // ONVIF service detected
                        CameraDevice camera = new CameraDevice();
                        camera.setIpAddress(ip);
                        camera.setPort(port);
                        camera.setName("ONVIF Camera");
                        camera.setRtspUrl("rtsp://" + ip + ":554/stream1");
                        
                        // Try to get device info
                        getDeviceInfo(camera);
                        
                        return camera;
                    }
                } catch (Exception e) {
                    // Port not responding
                }
            }
        } catch (Exception e) {
            // IP not reachable
        }
        return null;
    }

    private void getDeviceInfo(CameraDevice camera) {
        try {
            String soapBody = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\">\n" +
                    "<s:Body xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                    "xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">\n" +
                    "<GetDeviceInformation xmlns=\"http://www.onvif.org/ver10/device/wsdl\"/>\n" +
                    "</s:Body>\n" +
                    "</s:Envelope>";

            String url = "http://" + camera.getIpAddress() + ":" + camera.getPort() + "/onvif/device_service";
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);

            OutputStream os = conn.getOutputStream();
            os.write(soapBody.getBytes());
            os.flush();

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                parseDeviceInfo(camera, response.toString());
            }
            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Get device info error", e);
        }
    }

    private void parseDeviceInfo(CameraDevice camera, String response) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(response.getBytes()));

            // Extract manufacturer
            String[] tags = {"Manufacturer", "Model", "FirmwareVersion", "SerialNumber", "HardwareId"};
            for (String tag : tags) {
                NodeList nodes = doc.getElementsByTagName("tds:" + tag);
                if (nodes.getLength() == 0) {
                    nodes = doc.getElementsByTagName(tag);
                }
                if (nodes.getLength() > 0) {
                    String value = nodes.item(0).getTextContent();
                    switch (tag) {
                        case "Manufacturer":
                            camera.setManufacturer(value);
                            break;
                        case "Model":
                            camera.setModel(value);
                            camera.setName(value);
                            break;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Parse device info error", e);
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}
