import java.net.InetAddress;

public class Wavehand {
    // Sender send ACK and FIN
    public static void senderClose(FilteredSocket senderSocket, int seqNum, InetAddress CLIENT_IP_ADDR, int CLIENT_PORT) {
        //TODO
    }

    // Receiver send FIN and ACK
    public static void receiverClose(FilteredSocket socket, int seqNum, InetAddress SENDER_IP_ADDR, int SENDER_PORT) {
        //TODO
    }
}
