import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class Wavehand {
    // Sender send ACK and FIN
    public static void senderClose(FilteredSocket senderSocket, int seqNum, InetAddress CLIENT_IP_ADDR, int CLIENT_PORT) {
        // Init
        try {
            senderSocket.socket.setSoTimeout(1000);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
        //Create a FIN packet
        Segment finPacket = new Segment();
        finPacket.ack = finPacket.fin = true;
        finPacket.seqNum = seqNum;
        finPacket.data = new byte[0];
        //Send the packet to the receiver
        // Simulate packet process
        senderSocket.rawChannelSend(finPacket.toByteStream(), CLIENT_IP_ADDR, CLIENT_PORT);
        //Listen for ACK answer packet
        DatagramPacket receivePacket = senderSocket.receive();
        DatagramPacket receivePacket1 = senderSocket.receive();
        //Listen for next answer
        Segment receiveSeg = Segment.toSegment(receivePacket.getData());
        Segment receiveSeg1 = Segment.toSegment(receivePacket1.getData());
        if (receiveSeg == null || receiveSeg1 == null)
            System.exit(0);
        if (receiveSeg1.ack && receiveSeg1.fin && receiveSeg1.ackNum == seqNum + 1) {
            //final wave packet
            Segment ackPacket = new Segment();
            ackPacket.ack = true;
            ackPacket.seqNum = seqNum + 1;
            ackPacket.ackNum = receiveSeg1.seqNum + 1;
            ackPacket.data = new byte[0];
            // Simulate packet process
            senderSocket.rawChannelSend(ackPacket.toByteStream(), CLIENT_IP_ADDR, CLIENT_PORT);
        }
    }

    // Receiver send FIN and ACK
    public static void receiverClose(FilteredSocket socket, int seqNum, InetAddress SENDER_IP_ADDR, int SENDER_PORT) {
        // Init
        try {
            socket.socket.setSoTimeout(1000);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
        DatagramPacket receivePacket = socket.receive();
        Segment receiveSeg = Segment.toSegment(receivePacket.getData());
        if (receiveSeg == null)
            System.exit(0);
        //Send first answer
        if (receiveSeg.ack && receiveSeg.fin) {
            Segment finPacket = new Segment();
            finPacket.data = new byte[0];
            finPacket.ack = true;
            finPacket.seqNum = seqNum;
            finPacket.ackNum = receiveSeg.seqNum + 1;
            socket.rawChannelSend(finPacket.toByteStream(), SENDER_IP_ADDR, SENDER_PORT);
        }
        Segment finPacket1 = new Segment();
        finPacket1.data = new byte[0];
        finPacket1.ack = finPacket1.fin = true;
        finPacket1.ackNum = receiveSeg.seqNum + 1;
        finPacket1.seqNum = seqNum + 1;
        socket.rawChannelSend(finPacket1.toByteStream(), SENDER_IP_ADDR, SENDER_PORT);
    }
}
