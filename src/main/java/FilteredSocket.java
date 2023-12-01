import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class FilteredSocket {

    private final DatagramSocket socket;

    public FilteredSocket(int port) {
        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    public DatagramPacket receive() {
        byte[] receiveData = new byte[TCPSender.MSS];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        try {
            socket.receive(receivePacket);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return receivePacket;
    }

    // raw channel as demo
    public void rawChannelSend(byte[] segment, InetAddress dest, int destPort) {
        DatagramPacket sendPacket = new DatagramPacket(segment, segment.length, dest, destPort);
        try {
            socket.send(sendPacket);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // data loss
    public void lossyChannelSend(byte[] data, InetAddress dest) {
    }

    // data corruption
    public void noisyChannelSend(byte[] data, InetAddress dest) {
    }

    // data loss and corruption
    public void noisyAndLossyChannelSend(byte[] data, InetAddress dest) {
    }

}