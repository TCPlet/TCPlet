import java.net.DatagramPacket;
import java.util.TreeMap;

public class TCPSender {
    // Default Maximum Segment Size = 1460B
    public static int MSS = 1460;

    // Default Send Window Size = 2048B
    public static int SND_WND = 2048;
    public static FilteredSocket socket;
    public static Info receiver;
    private static int seqNum;

    /**
     * @param args
     * java TCPSender -p SENDER_PORT -d DATA
     */
    public static void main(String[] args) {
        int SENDER_PORT = Integer.parseInt(args[2]);
        byte[] data = args[4].getBytes();
        socket = new FilteredSocket(SENDER_PORT);
        receiver = Handshake.accept(socket);
        seqNum = receiver.seq;
        rdt(data);
        Wavehand.senderClose(socket, seqNum, receiver.IP, receiver.port);
    }

    private static void rdt(byte[] data) {
        // Fragmentation
        int len = data.length;
        TreeMap<Integer, byte[]> byteStream = new TreeMap<>();
        int prevSeq = seqNum;
        for (int start = 0; start < len; start += MSS) {
            int end = Math.min(start + MSS, len);
            byte[] arr = new byte[end - start];
            System.arraycopy(data, start, arr, 0, end - start);
            byteStream.put(prevSeq + end - start, arr);
            prevSeq += end - start;
        }
        // Transmission
        while (!byteStream.isEmpty()) {
            Segment segment = FilteredSocket.datagramPacket2Segment(socket.receive());
            while (segment == null) {
                segment = FilteredSocket.datagramPacket2Segment(socket.receive());
            }
        }

    }
}
