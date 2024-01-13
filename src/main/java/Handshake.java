import java.net.InetAddress;
import java.net.UnknownHostException;

public class Handshake {
    // handshake for sender: return client Info
    public static ReceiverInfo accept(FilteredSocket senderSocket) {
        //TODO
//        try {
//            ReceiverInfo receiverInfo = new ReceiverInfo(InetAddress.getByName("127.0.0.1"), 400, 1, 2048);
//            return receiverInfo;
//        } catch (UnknownHostException e) {
//            throw new RuntimeException(e);
//        }
        return null;
    }

    // handshake for receiver
    public static void connect(FilteredSocket receiverSocket, InetAddress SENDER_IP_ADDR, int SENDER_PORT) {
        //TODO
    }
}
