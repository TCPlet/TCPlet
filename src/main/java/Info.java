import java.net.InetAddress;

public class Info {
    InetAddress IP;
    int port;
    int seq;
    public Info(InetAddress ip, int p, int s) {
        this.IP = ip;
        this.port = p;
        this.seq = s;
    }
}
