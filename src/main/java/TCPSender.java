import java.net.*;

public class TCPSender {
    public void senderLoop(int WND_SIZE, int SENDER_PORT) {
        try (DatagramSocket socket = new DatagramSocket()) {
            String message = "Hello, Receiver!";
            byte[] sendData = message.getBytes();
            ReceiverInfo info = Handshake.accept(SENDER_PORT);
            InetAddress receiverAddress = InetAddress.getByName(info.IP_ADDR);

            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, receiverAddress, info.PORT);

            socket.send(sendPacket);

            System.out.println("Sent message to Receiver: " + message);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
