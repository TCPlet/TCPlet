import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.*;

public class FilteredSocket {

    private final DatagramSocket socket;
    Double outOrderChance = 0.01;
    Double contentChane = 0.01;
    Double lossChance = 0.01;
    //PriorityQueue Compare
    Comparator<BufferNode> customComparator = new Comparator<BufferNode>() {
        @Override
        public int compare(BufferNode o1, BufferNode o2) {
            // 根据元素的值进行比较，值越小优先级越高
            //根据checksum比较
            int num_o1 = o1.data[18] + o1.data[19];
            int num_o2 = o2.data[18] + o2.data[19];
            return Integer.compare(num_o1, num_o2);
        }
    };
    private PriorityQueue<BufferNode> buffer = new PriorityQueue<>(customComparator);

    public FilteredSocket(int port) {
        try {
            socket = new DatagramSocket(port);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    public static Segment datagramPacket2Segment(DatagramPacket datagramPacket) {
        byte[] data = datagramPacket.getData();
        return Segment.toSegment(data);
    }

    public DatagramPacket receive() {
        byte[] receiveData = new byte[20 + TCPSender.MSS];
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
        checkBuffer();
    }

    public void checkBuffer() {
        if (!buffer.isEmpty()) {
            BufferNode temp = buffer.poll();
            rawChannelSend(temp.data, temp.dest, temp.port);
        }
    }

    // data loss
    public void lossyChannelSend(byte[] data, InetAddress dest, int destPort) {
        //TODO
        if (smallChance(lossChance)) return;
        rawChannelSend(data, dest, destPort);
    }

    // data corruption
    public void noisyChannelSend(byte[] data, InetAddress dest, int destPort) {
        //TODO
        if (smallChance(contentChane)) {
            //corrupt content
            changeContent(data);
        }
        if (smallChance(outOrderChance)) {
            //change order
            BufferNode Node = new BufferNode(data, dest, destPort);
            buffer.add(Node);
            return;
        }
        rawChannelSend(data, dest, destPort);
    }

    // data loss and corruption
    public void noisyAndLossyChannelSend(byte[] data, InetAddress dest, int destPort) {
        //TODO
        if (halfChance()) {
            noisyChannelSend(data, dest, destPort);
        } else {
            lossyChannelSend(data, dest, destPort);
        }
    }

    private byte[] changeContent(byte[] data) {
        int len = data.length * 0.01 > 1 ? (int) (data.length * 0.01) : 1;
        Queue<Integer> Numqueue = new LinkedList<>();
        Random random = new Random();
        for (int i = 0; i < len; i++) Numqueue.add(random.nextInt(data.length));
        while (!Numqueue.isEmpty()) {
            int temp = random.nextInt(8);
            byte mask = (byte) (1 << temp);// eg:00001000
            int index = Numqueue.poll();
            data[index] = (byte) (data[index] ^ mask & 0xff);
        }
        return data;
    }

    private boolean smallChance(double small) {
        Random random = new Random();
        Double chance = random.nextDouble(1);
        return (chance < small);
    }

    private boolean halfChance() {
        Random random = new Random();
        return random.nextBoolean();
    }

    class BufferNode {
        byte[] data;
        InetAddress dest;
        int port;

        public BufferNode(byte[] data, InetAddress dest, int port) {
            this.data = data;
            this.dest = dest;
            this.port = port;
        }
    }
}
