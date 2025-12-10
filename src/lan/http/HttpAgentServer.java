package lan.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import lan.LanConfig;
import utils.PartitionOperations;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class HttpAgentServer {
    private static volatile boolean running = false;
    private static HttpServer server;

    public static synchronized void ensureStarted() {
        if (running) return;
        try {
            InetAddress bindAddr = InetAddress.getByName(LanConfig.getBindAddress());
            server = HttpServer.create(new InetSocketAddress(bindAddr, LanConfig.getPort()), 0);
            server.createContext("/ping", new PingHandler());
            server.createContext("/volumes", new VolumesHandler());
            server.createContext("/rename", new RenameHandler());
            server.createContext("/change-letter", new ChangeLetterHandler());
            server.createContext("/format", new FormatHandler());
            server.createContext("/delete", new DeleteHandler());
            server.createContext("/shrink", new ShrinkHandler());
            server.createContext("/extend", new ExtendHandler());
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.start();
            running = true;
        } catch (IOException e) {
            running = false;
        }
    }

    private static class DeleteHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                byte[] body = "Method Not Allowed\n".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(405, body.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(body); }
                return;
            }
            String bodyStr = readBody(ex);
            java.util.Map<String,String> form = parseForm(bodyStr);
            String drive = form.getOrDefault("drive", "").trim();
            if (drive.endsWith(":")) drive = drive.substring(0, drive.length()-1);
            if (drive.endsWith("\\")) drive = drive.substring(0, drive.length()-1);
            if (drive.length() == 2 && drive.charAt(1) == ':') drive = String.valueOf(drive.charAt(0));
            if (drive.length() != 1 || !Character.isLetter(drive.charAt(0))) {
                byte[] err = "ERROR: invalid drive\n".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(400, err.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(err); }
                return;
            }
            drive = ("" + Character.toUpperCase(drive.charAt(0)));
            if ("C".equalsIgnoreCase(drive)) {
                byte[] err = "ERROR: refusing to delete system drive C\n".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(400, err.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(err); }
                return;
            }
            String cmd = "$p=Get-Partition -DriveLetter " + drive + "; if($p){ Remove-Partition -DriveLetter " + drive + " -Confirm:$false } else { throw 'Partition not found' }";
            runAndRespond(ex, cmd);
        }
    }

    private static class RenameHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                byte[] body = "Method Not Allowed\n".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(405, body.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(body); }
                return;
            }
            String bodyStr = readBody(ex);
            java.util.Map<String,String> form = parseForm(bodyStr);
            String drive = form.getOrDefault("drive", "").trim();
            String label = form.getOrDefault("label", "").trim();
            if (drive.endsWith(":")) drive = drive.substring(0, drive.length()-1);
            if (drive.endsWith("\\")) drive = drive.substring(0, drive.length()-1);
            if (drive.length() == 2 && drive.charAt(1) == ':') drive = String.valueOf(drive.charAt(0));
            if (drive.length() != 1 || !Character.isLetter(drive.charAt(0))) {
                byte[] err = "ERROR: invalid drive\n".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(400, err.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(err); }
                return;
            }
            if (label.length() > 32) label = label.substring(0, 32);
            String escaped = label.replace("'", "''");
            String command = "Set-Volume -DriveLetter " + drive.toUpperCase() + " -NewFileSystemLabel '" + escaped + "'";
            int code;
            String out;
            try {
                ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", command);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
                }
                code = p.waitFor();
                out = sb.toString();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                code = -1; out = "Interrupted";
            } catch (Exception e) {
                code = -1; out = e.getMessage();
            }
            if (code == 0) {
                byte[] ok = "OK\n".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                ex.sendResponseHeaders(200, ok.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(ok); }
            } else {
                byte[] err = ("ERROR: " + (out == null ? "" : out)).getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                ex.sendResponseHeaders(500, err.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(err); }
            }
        }
    }

    private static class ChangeLetterHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                byte[] body = "Method Not Allowed\n".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(405, body.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(body); }
                return;
            }
            String bodyStr = readBody(ex);
            java.util.Map<String,String> form = parseForm(bodyStr);
            String oldDrive = form.getOrDefault("drive", "").trim();
            String newLetter = form.getOrDefault("new", "").trim();
            if (oldDrive.endsWith(":")) oldDrive = oldDrive.substring(0, oldDrive.length()-1);
            if (oldDrive.endsWith("\\")) oldDrive = oldDrive.substring(0, oldDrive.length()-1);
            if (oldDrive.length() == 2 && oldDrive.charAt(1) == ':') oldDrive = String.valueOf(oldDrive.charAt(0));
            if (newLetter.endsWith(":")) newLetter = newLetter.substring(0, newLetter.length()-1);
            if (newLetter.endsWith("\\")) newLetter = newLetter.substring(0, newLetter.length()-1);
            if (newLetter.length() == 2 && newLetter.charAt(1) == ':') newLetter = String.valueOf(newLetter.charAt(0));
            if (oldDrive.length() != 1 || newLetter.length() != 1 || !Character.isLetter(oldDrive.charAt(0)) || !Character.isLetter(newLetter.charAt(0))) {
                byte[] err = "ERROR: invalid letters\n".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(400, err.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(err); }
                return;
            }
            oldDrive = ("" + Character.toUpperCase(oldDrive.charAt(0)));
            newLetter = ("" + Character.toUpperCase(newLetter.charAt(0)));
            if ("C".equalsIgnoreCase(oldDrive)) {
                byte[] err = "ERROR: refusing to change system drive C\n".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(400, err.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(err); }
                return;
            }
            File tryNew = new File(newLetter + ":\\");
            if (tryNew.exists()) {
                byte[] err = "ERROR: target letter already in use\n".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(400, err.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(err); }
                return;
            }
            String command = "$old='" + oldDrive + "';$new='" + newLetter + "';$p=Get-Partition -DriveLetter $old; if($p){ Set-Partition -DriveLetter $old -NewDriveLetter $new -ErrorAction Stop } else { throw 'Partition not found' }";
            int code; String out;
            try {
                ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", command);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
                }
                code = p.waitFor(); out = sb.toString();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt(); code = -1; out = "Interrupted";
            } catch (Exception e) {
                code = -1; out = e.getMessage();
            }
            if (code == 0) {
                byte[] ok = "OK\n".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                ex.sendResponseHeaders(200, ok.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(ok); }
            } else {
                byte[] err = ("ERROR: " + (out == null ? "" : out)).getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                ex.sendResponseHeaders(500, err.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(err); }
            }
        }
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n; while ((n = is.read(buf)) > 0) baos.write(buf, 0, n);
            return baos.toString("UTF-8");
        }
    }

    private static java.util.Map<String,String> parseForm(String s) {
        java.util.Map<String,String> map = new java.util.HashMap<>();
        if (s == null || s.isEmpty()) return map;
        String[] pairs = s.split("&");
        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            String k = pair.substring(0, eq);
            String v = pair.substring(eq + 1);
            try {
                k = URLDecoder.decode(k, "UTF-8");
                v = URLDecoder.decode(v, "UTF-8");
            } catch (Exception ignored) {}
            map.put(k, v);
        }
        return map;
    }

    private static class PingHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            byte[] body = "OK PONG\n".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(body); }
        }
    }

    private static class VolumesHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            String command = "$ErrorActionPreference='Stop';" +
                    "Get-Volume | Where-Object { $_.DriveLetter } | ForEach-Object { " +
                    "$d=$_.DriveLetter; $lbl=$_.FileSystemLabel; if (-not $lbl) { $lbl='' }; " +
                    "$free=[int64]$_.SizeRemaining; $total=[int64]$_.Size; " +
                    "Write-Output (\"VOL`t\" + $d + \"`t\" + ($lbl -replace \"`t\", \" \") + \"`t\" + $free + \"`t\" + $total) }; " +
                    "Get-Disk | ForEach-Object { $disk=$_; $unalloc=[int64]($disk.Size - $disk.AllocatedSize); " +
                    "if ($unalloc -gt 0) { $name=$disk.FriendlyName; if (-not $name) { $name='Disk ' + $disk.Number }; " +
                    "Write-Output (\"FREE`t\" + $disk.Number + \"`t\" + ($name -replace \"`t\", \" \") + \"`t\" + $unalloc + \"`t\" + [int64]$disk.Size) } }";
            int code;
            String out;
            try {
                ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", command);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
                }
                code = p.waitFor();
                out = sb.toString();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                code = -1; out = "Interrupted";
            } catch (Exception e) {
                code = -1; out = e.getMessage();
            }

            StringBuilder resp = new StringBuilder();
            resp.append("OK\n");
            boolean wroteFromPowershell = false;
            if (code == 0 && out != null && !out.trim().isEmpty()) {
                resp.append(out.trim()).append('\n');
                wroteFromPowershell = true;
            }

            if (!wroteFromPowershell) {
                if (code != 0 && out != null && !out.isEmpty()) {
                    String warn = out.replace('\t', ' ').replace('\n', ' ').trim();
                    if (!warn.isEmpty()) {
                        resp.append("WARN\tFallback used: ").append(warn).append('\n');
                    }
                }
                File[] roots = File.listRoots();
                if (roots != null) {
                    for (File root : roots) {
                        String drive = root.getAbsolutePath().replace("\\", "").replace(":", "");
                        long free = root.getFreeSpace();
                        long total = root.getTotalSpace();
                        String label = PartitionOperations.getVolumeLabel(drive);
                        if (label == null) label = "";
                        resp.append("VOL\t").append(drive).append('\t')
                                .append(label.replace('\t', ' ')).append('\t')
                                .append(free).append('\t')
                                .append(total).append('\n');
                    }
                }
            }

            resp.append("END\n");
            byte[] body = resp.toString().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(body); }
        }
    }

    private static class FormatHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                byte[] body = "Method Not Allowed\n".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(405, body.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(body); }
                return;
            }
            String bodyStr = readBody(ex);
            java.util.Map<String,String> form = parseForm(bodyStr);
            String drive = form.getOrDefault("drive", "").trim();
            String fs = form.getOrDefault("fs", "NTFS").trim();
            String label = form.getOrDefault("label", "").trim();
            if (drive.endsWith(":")) drive = drive.substring(0, drive.length()-1);
            if (drive.endsWith("\\")) drive = drive.substring(0, drive.length()-1);
            if (drive.length() == 2 && drive.charAt(1) == ':') drive = String.valueOf(drive.charAt(0));
            if (drive.length() != 1 || !Character.isLetter(drive.charAt(0))) {
                byte[] err = "ERROR: invalid drive\n".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(400, err.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(err); }
                return;
            }
            drive = ("" + Character.toUpperCase(drive.charAt(0)));
            if ("C".equalsIgnoreCase(drive)) {
                byte[] err = "ERROR: refusing to format system drive C\n".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(400, err.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(err); }
                return;
            }
            if (!(fs.equalsIgnoreCase("NTFS") || fs.equalsIgnoreCase("FAT32") || fs.equalsIgnoreCase("exFAT"))) fs = "NTFS";
            String cmd = "Format-Volume -DriveLetter " + drive + " -FileSystem " + fs + (label.isEmpty()? "" : (" -NewFileSystemLabel '" + label.replace("'","''") + "'")) + " -Confirm:$false";
            runAndRespond(ex, cmd);
        }
    }

    private static class ShrinkHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                byte[] body = "Method Not Allowed\n".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(405, body.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(body); }
                return;
            }
            String bodyStr = readBody(ex);
            java.util.Map<String,String> form = parseForm(bodyStr);
            String drive = form.getOrDefault("drive", "").trim();
            String shrinkGbStr = form.getOrDefault("shrinkGB", "0").trim();
            if (drive.endsWith(":")) drive = drive.substring(0, drive.length()-1);
            if (drive.endsWith("\\")) drive = drive.substring(0, drive.length()-1);
            if (drive.length() == 2 && drive.charAt(1) == ':') drive = String.valueOf(drive.charAt(0));
            if (drive.length() != 1 || !Character.isLetter(drive.charAt(0))) {
                byte[] err = "ERROR: invalid drive\n".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(400, err.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(err); }
                return;
            }
            drive = ("" + Character.toUpperCase(drive.charAt(0)));
            double shrinkGB;
            try { shrinkGB = Double.parseDouble(shrinkGbStr); } catch (Exception e) { shrinkGB = 0.0; }
            if (shrinkGB <= 0) {
                byte[] err = "ERROR: invalid shrinkGB\n".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(400, err.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(err); }
                return;
            }
            String cmd = "$s=Get-PartitionSupportedSize -DriveLetter " + drive + "; $sizeMin=$s.SizeMin; $sizeMax=$s.SizeMax; " +
                    "$vol=Get-Volume -DriveLetter " + drive + "; $cur=$vol.Size; $target=[math]::Max($sizeMin, ($cur - " +
                    "([double]" + shrinkGB + "*1GB))); Resize-Partition -DriveLetter " + drive + " -Size $target -Confirm:$false";
            runAndRespond(ex, cmd);
        }
    }

    private static class ExtendHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                byte[] body = "Method Not Allowed\n".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(405, body.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(body); }
                return;
            }
            String bodyStr = readBody(ex);
            java.util.Map<String,String> form = parseForm(bodyStr);
            String drive = form.getOrDefault("drive", "").trim();
            String extendGbStr = form.getOrDefault("extendGB", "0").trim();
            if (drive.endsWith(":")) drive = drive.substring(0, drive.length()-1);
            if (drive.endsWith("\\")) drive = drive.substring(0, drive.length()-1);
            if (drive.length() == 2 && drive.charAt(1) == ':') drive = String.valueOf(drive.charAt(0));
            if (drive.length() != 1 || !Character.isLetter(drive.charAt(0))) {
                byte[] err = "ERROR: invalid drive\n".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(400, err.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(err); }
                return;
            }
            drive = ("" + Character.toUpperCase(drive.charAt(0)));
            double extendGB;
            try { extendGB = Double.parseDouble(extendGbStr); } catch (Exception e) { extendGB = 0.0; }
            if (extendGB <= 0) {
                byte[] err = "ERROR: invalid extendGB\n".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(400, err.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(err); }
                return;
            }
            String cmd = "$s=Get-PartitionSupportedSize -DriveLetter " + drive + "; $sizeMax=$s.SizeMax; " +
                    "$vol=Get-Volume -DriveLetter " + drive + "; $cur=$vol.Size; $target=[math]::Min($sizeMax, ($cur + " +
                    "([double]" + extendGB + "*1GB))); Resize-Partition -DriveLetter " + drive + " -Size $target -Confirm:$false";
            runAndRespond(ex, cmd);
        }
    }

    private static void runAndRespond(HttpExchange ex, String command) throws IOException {
        int code; String out;
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", "$ErrorActionPreference='Stop'; " + command);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
            }
            code = p.waitFor(); out = sb.toString();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt(); code = -1; out = "Interrupted";
        } catch (Exception e) {
            code = -1; out = e.getMessage();
        }
        if (code == 0) {
            byte[] ok = "OK\n".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(200, ok.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(ok); }
        } else {
            byte[] err = ("ERROR: " + (out == null ? "" : out)).getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(500, err.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(err); }
        }
    }
}
