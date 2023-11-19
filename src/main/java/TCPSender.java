import java.net.*;

public class TCPSender {
    public static void main(String[] args) {
        try (DatagramSocket socket = new DatagramSocket()) {
            // 构造发送的数据包
            String message = "Hello, Receiver!";
            byte[] sendData = message.getBytes();
            InetAddress receiverAddress = InetAddress.getByName("192.168.1.2"); // 接收端的 IP 地址
            int receiverPort = 9876; // 接收端的端口号

            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, receiverAddress, receiverPort);

            // 发送数据包
            socket.send(sendPacket);

            System.out.println("Sent message to Receiver: " + message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
