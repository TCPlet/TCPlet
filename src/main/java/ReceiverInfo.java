import java.net.InetAddress;

public class ReceiverInfo {
    InetAddress IP;
    int port;
    int seq;
    int rcvWnd;

    public ReceiverInfo(InetAddress ip, int port, int seq, int rcvWnd) {
        this.IP = ip;
        this.port = port;
        this.seq = seq;
        this.rcvWnd = rcvWnd;

    }
}
