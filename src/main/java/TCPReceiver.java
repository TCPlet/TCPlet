import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class TCPReceiver {

    public ReentrantLock lock = new ReentrantLock();
    public Condition full = lock.newCondition();
    public Condition starving = lock.newCondition();
    public FilteredSocket socket;
    public int RCV_WINDOW = 64 * 1024;
    // <seqNum, Segment>
    private TreeMap<Integer, Segment> window = new TreeMap<>();
    private int ackNum = -1;
    public int MSS = 1460;
    InetAddress Sender_IP;
    int Sender_port;
    boolean term = false;
    String output;

    TCPReceiver(FilteredSocket socket, InetAddress Sender_IP, int Sender_port) {
        this.socket = socket;
        this.Sender_port = Sender_port;
        this.Sender_IP = Sender_IP;
    }

    /**
     * @param args java TCPReceiver -s SERVER_IP -p SERVER_PORT -f FILE_NAME
     */
    public static void main(String[] args) {

        InetAddress Sender_IP;
        try {
            Sender_IP = InetAddress.getByName(args[1]);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        int Sender_port = Integer.parseInt(args[3]);
        FilteredSocket socket = new FilteredSocket(11451);

        TCPReceiver receiver = new TCPReceiver(socket, Sender_IP, Sender_port);
        receiver.ackNum = Handshake.connect(socket, Sender_IP, Sender_port);
        receiver.output = args[5];

        receiver.rdt();
    }

    class Output implements Runnable {
        @Override
        public void run() {
            String filePath = output; // 目标文件路径
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(filePath);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
            while (!term) {
                lock.lock();
                if (window.isEmpty()) {
                    try {
                        starving.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                Map.Entry<Integer, Segment> e = window.pollFirstEntry();
                System.out.printf("Window size: %d, smallest seq: %d, ackNum: %d\n", window.size(), e.getValue().seqNum, ackNum);
                if (e.getValue().seqNum == ackNum) {
                    ackNum += e.getValue().data.length;
                    RCV_WINDOW += e.getValue().data.length;
                    try {
                        // 将字节数组写入文件
                        // TODO: Debug
                        fos.write(e.getValue().data);
                        System.out.printf("Bytes %d ~ %d written to file successfully.\n", e.getValue().seqNum, e.getValue().seqNum + e.getValue().data.length);
                    } catch (IOException err) {
                        throw new RuntimeException();
                    }
                    System.out.printf("Removed: seq %d\n", e.getValue().seqNum);
                } else {
                    window.put(e.getKey(), e.getValue());
                    try {
                        starving.await();
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
                full.signal();
                lock.unlock();
            }
            for (Map.Entry<Integer, Segment> en : window.entrySet()) {
                try {
                    // 将字节数组写入文件
                    // TODO: Debug
                    fos.write(en.getValue().data);
                    System.out.printf("Bytes %d ~ %d written to file successfully.\n", en.getValue().seqNum, en.getValue().seqNum + en.getValue().data.length);
                } catch (IOException err) {
                    throw new RuntimeException();
                }
            }
            try {
                fos.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    class Receiver implements Runnable {
        @Override
        public void run() {
            while (true) {
                Segment received = FilteredSocket.datagramPacket2Segment(socket.receive());
                while (received == null) {
                    System.out.println("Corrupted packet received");
                    received = FilteredSocket.datagramPacket2Segment(socket.receive());
                }
                // TODO: DEBUG
                //挥手
                if (received.fin) {
                    term = true;
                    Wavehand.receiverClose(socket, ackNum, Sender_IP, Sender_port);
                    break;
                }
                Segment ack = new Segment();
                lock.lock();
                if (received.data.length != 0 && RCV_WINDOW >= received.data.length && received.seqNum + received.data.length > ackNum && !window.containsKey(received.seqNum)) {
                    // 1. not zero probing segment
                    // 2. enough buffer space
                    // 3. not acked before
                    window.put(received.seqNum, received);
                    ack.sackNum = received.seqNum + received.data.length;
                    RCV_WINDOW -= received.data.length;
                    starving.signal();
                }
                lock.unlock();
                // TODO: DEBUG

                ack.ackNum = ackNum;
                ack.data = new byte[0];
                ack.rcvWnd = RCV_WINDOW;

                System.out.printf("DATA received: seq %d, len %d\n", received.seqNum, received.data.length);
                System.out.printf("ACK sent: ack %d, sack %d, wnd %d\n", ack.ackNum, ack.sackNum, ack.rcvWnd);

                send(ack);
            }
        }
    }

    private void rdt() {
        Thread output = new Thread(new Output());
        Thread receiver = new Thread(new Receiver());
        output.start();
        receiver.start();
        try {
            output.join();
            receiver.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void send(Segment seg) {
        socket.noisyAndLossyChannelSend(seg.toByteStream(), Sender_IP, Sender_port);
    }
}

