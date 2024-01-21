import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.Random;

public class Wavehand {
    // Sender send ACK and FIN
    public static void senderClose(FilteredSocket senderSocket, int seqNum, InetAddress CLIENT_IP_ADDR, int CLIENT_PORT) {
        //Creat a FIN packet
        Segment finPacket = new Segment();
        finPacket.ack = finPacket.fin = true;
        finPacket.seqNum = seqNum;
        //Send the packet to the receiver
        senderSocket.rawChannelSend(finPacket.toByteStream(), CLIENT_IP_ADDR, CLIENT_PORT);
        //Listen for ACK answer packet
        DatagramPacket receivePacket = senderSocket.receive();
        Segment receiveSeg = Segment.toSegment(receivePacket.getData());
        //Simulate fin_wait
        assert receiveSeg != null;
        if (receiveSeg.ack && !receiveSeg.fin) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
        }
        //Listen for next answer
        DatagramPacket receivePacket1 = senderSocket.receive();
        Segment receiveSeg1 = Segment.toSegment(receivePacket1.getData());
        assert receiveSeg1 != null;
        if (receiveSeg1.ack && receiveSeg1.fin && receiveSeg1.ackNum == seqNum + 1) {
            //final wave packet
            Segment ackPacket = new Segment();
            ackPacket.ack = true;
            ackPacket.seqNum = seqNum + 1;
            ackPacket.ackNum = receiveSeg1.seqNum + 1;
            senderSocket.rawChannelSend(ackPacket.toByteStream(), CLIENT_IP_ADDR, CLIENT_PORT);
        }
    }

    // Receiver send FIN and ACK
    public static void receiverClose(FilteredSocket socket, int seqNum, InetAddress SENDER_IP_ADDR, int SENDER_PORT) {
        DatagramPacket receivePacket = socket.receive();
        Segment receiveSeg = Segment.toSegment(receivePacket.getData());
        //Send first answer
        if (receiveSeg.ack && receiveSeg.fin) {
            Segment finPacket = new Segment();
            finPacket.ack = true;
            finPacket.seqNum = seqNum;
            finPacket.ackNum = receiveSeg.seqNum + 1;
            socket.rawChannelSend(finPacket.toByteStream(), SENDER_IP_ADDR, SENDER_PORT);
        }
        //Simulate data transport
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
        }
        Random random = new Random();
        int next_seqNum = random.nextInt();
        Segment finPacket1 = new Segment();
        finPacket1.ack = finPacket1.fin = true;
        finPacket1.ackNum = receiveSeg.seqNum + 1;
        finPacket1.seqNum = next_seqNum;
        socket.rawChannelSend(finPacket1.toByteStream(), SENDER_IP_ADDR, SENDER_PORT);
    }
}
