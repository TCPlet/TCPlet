import java.net.*;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TCPReceiver {

    private final Lock lock = new ReentrantLock();
    public FilteredSocket socket;
    public static int RCV_WINDOW = 2048;

    public boolean is_finish = false;
    private final ArrayList<Segment> wait_to_send = new ArrayList<>();

    //初始设为负，表示从未接受过数据
    private static int PRE_ack = -1;
    public static int MSS = 1460;
    InetAddress Sender_IP;
    int Sender_port;

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
        FilteredSocket socket = new FilteredSocket(Sender_port);


        TCPReceiver receiver = new TCPReceiver(socket, Sender_IP, Sender_port);
        Handshake.connect(socket, Sender_IP, Sender_port);

        receiver.rdt();


    }

    class Receive_from_Sender implements Runnable {

        @Override
        public void run() {
            while (true) {
                Segment segment = FilteredSocket.datagramPacket2Segment(socket.receive());
                //挥手
                if (segment.fin) {
                    Wavehand.receiverClose(socket, PRE_ack, Sender_IP, Sender_port);
                    is_finish = true;
                    break;
                }

                if (PRE_ack < 0 || PRE_ack == segment.seqNum) {
                    PRE_ack = segment.seqNum + segment.data.length + 1;
                    segment.ackNum = PRE_ack;
                    segment.sackNum = segment.ackNum;
                } else {
                    segment.ackNum = PRE_ack;
                    segment.sackNum = segment.ackNum;
                }

                lock.lock();
                RCV_WINDOW -= MSS;
                wait_to_send.add(segment);
                lock.unlock();
            }
        }


    }

    class Send_to_Sender implements Runnable {
        @Override
        public void run() {


            while (true) {
                if (is_finish && wait_to_send.size() == 0) {
                    break;
                }
                lock.lock();
                if (wait_to_send.size() > 0) {

                    Segment newseg = wait_to_send.get(0);
                    RCV_WINDOW += MSS;
                    wait_to_send.remove(0);
                    newseg.rcvWnd = RCV_WINDOW;
                    socket.rawChannelSend(newseg.toByteStream(), Sender_IP, Sender_port);

                }
                lock.unlock();

            }
        }
    }

    private void rdt() {
        Thread Receive_from_Sender = new Thread(new TCPReceiver.Receive_from_Sender());
        Thread Send_to_Sender = new Thread(new TCPReceiver.Send_to_Sender());
        Receive_from_Sender.start();
        Send_to_Sender.start();

    }
}

