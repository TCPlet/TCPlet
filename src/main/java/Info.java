import java.net.InetAddress;

public class Info {
    InetAddress IP;
    int port;
    public Info(InetAddress s, int p) {
        this.IP = s;
        this.port = p;
    }
}
