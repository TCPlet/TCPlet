import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TCPReceiver {

    public ReentrantLock lock = new ReentrantLock();
    public FilteredSocket socket;
    public int RCV_WINDOW = 64 * 1024;
    // <seqNum, Segment>
    private TreeMap<Integer, Segment> window = new TreeMap<>();
    private int ackNum = -1;
    public int MSS = 1460;
    InetAddress Sender_IP;
    int Sender_port;
    boolean term = false;

    TCPReceiver(FilteredSocket socket, InetAddress Sender_IP, int Sender_port) {
        this.socket = socket;
        this.Sender_port = Sender_port;
        this.Sender_IP = Sender_IP;
    }

    /**
     * @param args java TCPReceiver -s SERVER_IP -p SERVER_PORT
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

        receiver.rdt();
    }

    class Output implements Runnable {
        @Override
        public void run() {
            String filePath = "output.txt"; // 目标文件路径
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(filePath);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
            while (!term) {
                lock.lock();
                while (!window.isEmpty()) {
                    Map.Entry<Integer, Segment> e = window.pollFirstEntry();
                    if (e.getValue().seqNum == ackNum) {
                        ackNum += e.getValue().ackNum + e.getValue().data.length;
                        RCV_WINDOW += e.getValue().data.length;
//                        System.out.println(Arrays.toString(e1.getValue().data));
                        try {
                            // 将字节数组写入文件
                            // TODO: Debug
                            System.out.println(new String(e.getValue().data));
                            fos.write(e.getValue().data);
                            System.out.printf("Bytes %d ~ %d written to file successfully.\n", e.getValue().seqNum, e.getValue().seqNum + e.getValue().data.length);
                        } catch (IOException err) {
                            throw new RuntimeException();
                        }
                        System.out.printf("Removed: seq %d\n", e.getValue().seqNum);
                    } else {
                        window.put(e.getKey(), e.getValue());
                        break;
                    }
                }
                lock.unlock();
            }
            try {
                fos.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void rdt() {
        Thread output = new Thread(new Output());
        output.start();
        while (true) {
            Segment received = FilteredSocket.datagramPacket2Segment(socket.receive());
            while (received == null) {
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
            if (received.data.length != 0 && RCV_WINDOW >= received.data.length) {
                window.put(received.seqNum, received);
                RCV_WINDOW -= received.data.length;
                ack.ackNum = ackNum;
                ack.sackNum = received.seqNum + received.data.length;
            }
            lock.unlock();
            // TODO: DEBUG

            ack.data = new byte[0];
            ack.rcvWnd = RCV_WINDOW;

            System.out.printf("ACK sent: ack %d, sack %d, wnd %d\n", ack.ackNum, ack.sackNum, ack.rcvWnd);
            System.out.printf("DATA received: seq %d, len %d\n", received.seqNum, received.data.length);

            socket.rawChannelSend(ack.toByteStream(), Sender_IP, Sender_port);
        }
    }
}

