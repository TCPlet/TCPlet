import java.net.*;

public class TCPReceiver {
    public static void main(String[] args) {
        try (DatagramSocket socket = new DatagramSocket(9876)) {
            // 接收数据包
            byte[] receiveData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            socket.receive(receivePacket);

            // 解析接收到的数据
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
