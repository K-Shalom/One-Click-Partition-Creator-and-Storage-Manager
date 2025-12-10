package lan;

import utils.PartitionOperations;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

public class AgentServer {
    private static volatile boolean running = false;
    private static Thread serverThread;

    public static synchronized void ensureStarted() {
        if (running) return;
        running = true;
        serverThread = new Thread(AgentServer::run, "AgentServer");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private static void run() {
        ServerSocket server = null;
        try {
            InetAddress bindAddr = InetAddress.getByName(LanConfig.getBindAddress());
            server = new ServerSocket(LanConfig.getPort(), 50, bindAddr);
            while (running) {
                try {
                    Socket client = server.accept();
                    new Thread(() -> handle(client), "AgentClientHandler").start();
                } catch (IOException ignored) {}
            }
        } catch (IOException e) {
            running = false;
        } finally {
            if (server != null) try { server.close(); } catch (IOException ignored) {}
        }
    }

    private static void handle(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
            String line = in.readLine();
            if (line == null) return;
            String cmd = line.trim().toUpperCase();
            if (cmd.startsWith("PING")) {
                writeLine(out, "OK PONG");
                return;
            }
            if (cmd.startsWith("LIST_VOLUMES")) {
                List<Vol> vols = listVolumes();
                writeLine(out, "OK");
                for (Vol v : vols) {
                    writeLine(out, "VOL\t" + v.drive + "\t" + (v.label == null ? "" : v.label) + "\t" + v.free + "\t" + v.total);
                }
                writeLine(out, "END");
                return;
            }
            writeLine(out, "ERR\tUNKNOWN_COMMAND");
        } catch (IOException ignored) {
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private static void writeLine(BufferedWriter out, String s) throws IOException {
        out.write(s);
        out.write("\n");
        out.flush();
    }

    private static class Vol {
        String drive;
        String label;
        long free;
        long total;
    }

    private static List<Vol> listVolumes() {
        List<Vol> result = new ArrayList<>();
        File[] roots = File.listRoots();
        if (roots != null) {
            for (File root : roots) {
                Vol v = new Vol();
                v.drive = root.getAbsolutePath().replace("\\", "").replace(":", "");
                v.free = root.getFreeSpace();
                v.total = root.getTotalSpace();
                String label = PartitionOperations.getVolumeLabel(v.drive);
                v.label = (label == null ? "" : label.trim());
                result.add(v);
            }
        }
        return result;
    }
}
