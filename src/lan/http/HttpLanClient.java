package lan.http;

import lan.LanClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class HttpLanClient {

    public static boolean ping(String host, int port) {
        try {
            URL url = new URL("http", host, port, "/ping");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            if (code != 200) return false;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line = br.readLine();
                return line != null && line.startsWith("OK");
            }
        } catch (IOException e) {
            return false;
        }
    }

    public static List<LanClient.RemoteVolume> listVolumes(String host, int port) throws IOException {
        URL url = new URL("http", host, port, "/volumes");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(2500);
        conn.setReadTimeout(8000);
        conn.setRequestMethod("GET");
        int code = conn.getResponseCode();
        if (code != 200) throw new IOException("HTTP status " + code);
        ArrayList<LanClient.RemoteVolume> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String first = br.readLine();
            if (first == null || !first.startsWith("OK")) {
                throw new IOException("Remote error or no response");
            }
            String line;
            while ((line = br.readLine()) != null) {
                if (line.equals("END")) break;
                if (!line.startsWith("VOL\t") && !line.startsWith("FREE\t")) continue;
                String[] parts = line.split("\t");
                if (parts[0].equals("VOL") && parts.length >= 5) {
                    String drive = parts[1];
                    String label = parts[2];
                    long free = parseLongSafe(parts[3]);
                    long total = parseLongSafe(parts[4]);
                    list.add(new LanClient.RemoteVolume(drive + ":", label, free, total));
                } else if (parts[0].equals("FREE") && parts.length >= 5) {
                    String diskId = parts[1];
                    String name = parts[2];
                    long free = parseLongSafe(parts[3]);
                    long total = parseLongSafe(parts[4]);
                    list.add(new LanClient.RemoteVolume("Disk " + diskId + " (Unallocated)", name, free, total));
                }
            }
        }
        return list;
    }

    public static boolean renameVolume(String host, int port, String drive, String newLabel) throws IOException {
        URL url = new URL("http", host, port, "/rename");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(10000);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        conn.setDoOutput(true);
        String body = "drive=" + URLEncoder.encode(drive, "UTF-8") +
                "&label=" + URLEncoder.encode(newLabel, "UTF-8");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }
        int code = conn.getResponseCode();
        if (code == 200) return true;
        String err;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                (conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream()),
                StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
            err = sb.toString();
        }
        throw new IOException(err.isEmpty() ? ("HTTP " + code) : err.trim());
    }

    public static boolean formatVolume(String host, int port, String drive, String fs, String label) throws IOException {
        URL url = new URL("http", host, port, "/format");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(600000); // formatting can take long
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        conn.setDoOutput(true);
        String body = "drive=" + URLEncoder.encode(drive, "UTF-8") +
                "&fs=" + URLEncoder.encode(fs, "UTF-8") +
                "&label=" + URLEncoder.encode(label == null ? "" : label, "UTF-8");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        try (java.io.OutputStream os = conn.getOutputStream()) { os.write(bytes); }
        int code = conn.getResponseCode();
        if (code == 200) return true;
        String err;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                (conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream()),
                StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
            err = sb.toString();
        }
        throw new IOException(err.isEmpty() ? ("HTTP " + code) : err.trim());
    }

    public static boolean deleteVolume(String host, int port, String drive) throws IOException {
        URL url = new URL("http", host, port, "/delete");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(600000);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        conn.setDoOutput(true);
        String body = "drive=" + URLEncoder.encode(drive, "UTF-8");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        try (java.io.OutputStream os = conn.getOutputStream()) { os.write(bytes); }
        int code = conn.getResponseCode();
        if (code == 200) return true;
        String err;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                (conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream()),
                StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
            err = sb.toString();
        }
        throw new IOException(err.isEmpty() ? ("HTTP " + code) : err.trim());
    }

    public static boolean shrinkVolume(String host, int port, String drive, double shrinkGB) throws IOException {
        URL url = new URL("http", host, port, "/shrink");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(300000);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        conn.setDoOutput(true);
        String body = "drive=" + URLEncoder.encode(drive, "UTF-8") +
                "&shrinkGB=" + URLEncoder.encode(String.valueOf(shrinkGB), "UTF-8");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        try (java.io.OutputStream os = conn.getOutputStream()) { os.write(bytes); }
        int code = conn.getResponseCode();
        if (code == 200) return true;
        String err;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                (conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream()),
                StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
            err = sb.toString();
        }
        throw new IOException(err.isEmpty() ? ("HTTP " + code) : err.trim());
    }

    public static boolean extendVolume(String host, int port, String drive, double extendGB) throws IOException {
        URL url = new URL("http", host, port, "/extend");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(300000);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        conn.setDoOutput(true);
        String body = "drive=" + URLEncoder.encode(drive, "UTF-8") +
                "&extendGB=" + URLEncoder.encode(String.valueOf(extendGB), "UTF-8");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        try (java.io.OutputStream os = conn.getOutputStream()) { os.write(bytes); }
        int code = conn.getResponseCode();
        if (code == 200) return true;
        String err;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                (conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream()),
                StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
            err = sb.toString();
        }
        throw new IOException(err.isEmpty() ? ("HTTP " + code) : err.trim());
    }

    public static boolean changeDriveLetter(String host, int port, String drive, String newLetter) throws IOException {
        URL url = new URL("http", host, port, "/change-letter");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(15000);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        conn.setDoOutput(true);
        String body = "drive=" + URLEncoder.encode(drive, "UTF-8") +
                "&new=" + URLEncoder.encode(newLetter, "UTF-8");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }
        int code = conn.getResponseCode();
        if (code == 200) return true;
        String err;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                (conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream()),
                StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
            err = sb.toString();
        }
        throw new IOException(err.isEmpty() ? ("HTTP " + code) : err.trim());
    }

    private static long parseLongSafe(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return -1L; }
    }
}
