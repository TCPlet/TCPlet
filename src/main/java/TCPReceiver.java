import java.net.*;

public class TCPReceiver {
    public void receiverLoop(int SENDER_IP_ADDR, int SENDER_IP, int SELF_PORT) {
        try (DatagramSocket socket = new DatagramSocket(SELF_PORT)) {
            byte[] receiveData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            socket.receive(receivePacket);

            String receivedMessage = new String(receivePacket.getData(), 0, receivePacket.getLength());
            InetAddress senderAddress = receivePacket.getAddress();
            int senderPort = receivePacket.getPort();

            System.out.println("Received message from Sender: " + receivedMessage);
            System.out.println("Sender's Address: " + senderAddress);
            System.out.println("Sender's Port: " + senderPort);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
