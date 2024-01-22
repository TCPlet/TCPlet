import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.Random;

public class Handshake {
    // handshake for sender: return client Info
    public static ReceiverInfo accept(FilteredSocket senderSocket) {
        // Wait for the SYN packet from the sender
        DatagramPacket synPacket = senderSocket.receive();
        Segment synSegment = Segment.toSegment(synPacket.getData());

        // Validate the SYN packet
        if (synSegment != null && synSegment.syn) {
            // Generate a random sequence number for the initial ACK packet
            Random random = new Random();
            int initialSeqNum = Math.abs(random.nextInt()) % 10000;

            // Create a SYN-ACK packet
            Segment synAckPacket = new Segment();
            synAckPacket.syn = true;
            synAckPacket.ack = true;
            synAckPacket.seqNum = initialSeqNum;
            synAckPacket.ackNum = synSegment.seqNum + 1;
            synAckPacket.data = new byte[0];


            // Send the SYN-ACK packet to the sender
            senderSocket.rawChannelSend(synAckPacket.toByteStream(), synPacket.getAddress(), synPacket.getPort());

            // Wait for the ACK packet from the sender
            DatagramPacket ackPacket = senderSocket.receive();
            Segment ackSegment = Segment.toSegment(ackPacket.getData());

            // Validate the ACK packet
            if (ackSegment != null && ackSegment.ack && ackSegment.ackNum == initialSeqNum + 1) {
                // Handshake successful

                return new ReceiverInfo(ackPacket.getAddress(), ackPacket.getPort(), ackSegment.ackNum, ackSegment.rcvWnd);
            }
        }

        throw new RuntimeException("Handshake failed");

    }

    // handshake for receiver
    public static int connect(FilteredSocket receiverSocket, InetAddress SENDER_IP_ADDR, int SENDER_PORT) {

        Random random = new Random();
        int initialSeqNum = Math.abs(random.nextInt()) % 10000;

        // Create a SYN packet
        Segment synPacket = new Segment();
        synPacket.syn = true;
        synPacket.seqNum = initialSeqNum;
        synPacket.data = new byte[0];

        // Send the SYN packet to the sender
        receiverSocket.rawChannelSend(synPacket.toByteStream(), SENDER_IP_ADDR, SENDER_PORT);

        // Wait for the SYN-ACK packet from the sender
        DatagramPacket synAckPacket = receiverSocket.receive();
        Segment synAckSegment = Segment.toSegment(synAckPacket.getData());

        // Validate the SYN-ACK packet
        Segment ackPacket = new Segment();
        if (synAckSegment != null && synAckSegment.syn && synAckSegment.ack && synAckSegment.ackNum == initialSeqNum + 1) {
            // Create an ACK packet
            ackPacket.ack = true;
            ackPacket.ackNum = synAckSegment.seqNum + 1;
            ackPacket.rcvWnd = 64 * 1024;
            ackPacket.data = new byte[0];
            receiverSocket.rawChannelSend(ackPacket.toByteStream(), SENDER_IP_ADDR, SENDER_PORT);
        } else {
            throw new RuntimeException("Handshake failed");
        }
        return ackPacket.ackNum;
    }

}
