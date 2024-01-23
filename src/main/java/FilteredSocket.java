import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;

public class FilteredSocket {

    final DatagramSocket socket;
    Random random = new Random();
    int outOrderChance = 1;
    int contentChane = 10;
    int lossChance = 10;
    private final PriorityBlockingQueue<BufferNode> buffer = new PriorityBlockingQueue<>();

    public FilteredSocket(int port) {
        try {
            socket = new DatagramSocket(port);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    public static Segment datagramPacket2Segment(DatagramPacket datagramPacket) {
        byte[] data = datagramPacket.getData();
        int actLen = datagramPacket.getLength();
        byte[] rv = new byte[actLen];
        System.arraycopy(data, 0, rv, 0, actLen);
        return Segment.toSegment(rv);
    }

    public DatagramPacket receive() {
        byte[] receiveData = new byte[20 + TCPSender.MSS];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        try {
            socket.receive(receivePacket);
        } catch (SocketTimeoutException e) {
            System.exit(0);
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
        if (!buffer.isEmpty()) {
            checkBuffer();
        }
    }

    public void checkBuffer() {
        BufferNode temp = buffer.poll();
        if (temp == null) {
            return;
        }
        rawChannelSend(temp.data, temp.dest, temp.port);
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
        if (smallChance(outOrderChance) && buffer.size() < 2) {
            //change order
            BufferNode Node = new BufferNode(data, dest, destPort);
            buffer.offer(Node);
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
        for (int i = 0; i < len; i++) Numqueue.add(random.nextInt(data.length));
        while (!Numqueue.isEmpty()) {
            int temp = random.nextInt(8);
            byte mask = (byte) (1 << temp);// eg:00001000
            int index = Numqueue.poll();
            data[index] = (byte) (data[index] ^ mask & 0xff);
        }
        return data;
    }

    private boolean smallChance(int small) {
        int chance = random.nextInt(1000);
        return (chance <= small);
    }

    private boolean halfChance() {
        return random.nextInt() < 0.5;
    }

    class BufferNode implements Comparable<BufferNode> {
        byte[] data;
        InetAddress dest;
        int port;

        public BufferNode(byte[] data, InetAddress dest, int port) {
            this.data = data;
            this.dest = dest;
            this.port = port;
        }

        @Override
        public int compareTo(BufferNode o) {
            return this.hashCode() - o.hashCode();
        }
    }
}
