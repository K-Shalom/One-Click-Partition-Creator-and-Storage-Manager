package lan;

import java.io.FileInputStream;
import java.util.Properties;

public class LanConfig {
    private static int port = 5055;
    private static String bindAddress = "0.0.0.0";
    private static String protocol = "http"; // http or tcp

    static {
        try {
            Properties props = new Properties();
            FileInputStream fis = new FileInputStream("config/lan.properties");
            props.load(fis);
            fis.close();
            String p = props.getProperty("lan.port");
            if (p != null && !p.trim().isEmpty()) {
                port = Integer.parseInt(p.trim());
            }
            String bind = props.getProperty("lan.bind", props.getProperty("lan.host"));
            if (bind != null && !bind.trim().isEmpty()) {
                bindAddress = bind.trim();
            }
            String proto = props.getProperty("lan.protocol");
            if (proto != null && !proto.trim().isEmpty()) {
                protocol = proto.trim().toLowerCase();
            }
        } catch (Exception ignored) {
            // use defaults
        }
    }

    public static int getPort() { return port; }
    public static String getBindAddress() { return bindAddress; }
    public static String getProtocol() { return protocol; }
}
