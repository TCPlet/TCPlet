import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TCPSender {
    private static class NotACKedSegment {
        Segment segment;
        int delay = EstimatedRTT.get();
        int ackNum;
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                socket.rawChannelSend(segment.toByteStream(), receiver.IP, receiver.port);
                while (notACKed.containsKey(ackNum)) {
                    timer.schedule(task, delay);
                    delay *= 2;
                }
            }
        };

        NotACKedSegment(Segment segment) {
            this.segment = segment;
            ackNum = segment.seqNum + segment.data.length;
        }
    }

    // Default Maximum Segment Size = 1460B
    public static int MSS = 1460;

    // Default Send Window Size = 2048B
    public static int SND_WND = 2048;
    public static FilteredSocket socket;
    public static ReceiverInfo receiver;
    private static AtomicInteger RCV_WND;
    // <ackNum, Segment>
    private static final ConcurrentNavigableMap<Integer, Segment> segmentStream = new ConcurrentSkipListMap<>();
    private static final ConcurrentNavigableMap<Integer, NotACKedSegment> notACKed = new ConcurrentSkipListMap<>();

    private static AtomicInteger EstimatedRTT = new AtomicInteger(1000);
    private static AtomicInteger DevRTT = new AtomicInteger(0);
    private static final Lock notACKedLock = new ReentrantLock();
    private static final Condition notACKedCondition = notACKedLock.newCondition();

    /**
     * @param args java TCPSender -p SENDER_PORT -d DATA
     */
    public static void main(String[] args) {
        int SENDER_PORT = Integer.parseInt(args[1]);
        byte[] data = args[3].getBytes();
        socket = new FilteredSocket(SENDER_PORT);
        receiver = Handshake.accept(socket);
        assert (receiver != null);
        int seqNum = receiver.seq;
        RCV_WND = new AtomicInteger(receiver.rcvWnd);
        // Fragmentation
        int len = data.length;
        int prevSeq = seqNum;
        for (int start = 0; start < len; start += MSS) {
            int end = Math.min(start + MSS, len);
            byte[] arr = new byte[end - start];
            System.arraycopy(data, start, arr, 0, end - start);
            Segment segment = new Segment();
            segment.seqNum = prevSeq + end - start;
            segment.data = arr;
            segmentStream.put(segment.seqNum + arr.length, segment);
            prevSeq += end - start;
        }
        rdt();
        Wavehand.senderClose(socket, seqNum, receiver.IP, receiver.port);
    }


    static class MainSender implements Runnable {
        @Override
        public void run() {
            try {
                // Event:
                // 1. RCV_WND && SND_WND: lastSent - lastACK >= min(SND_WND, RCV_WND)
                // 2. ACK received: window moving forward
                // 3. Timeout Retransmit
                // 4. Fast Retransmit
                notACKedLock.lock();
                while (!segmentStream.isEmpty()) {
                    if (notACKed.size() * MSS >= Math.min(SND_WND, RCV_WND.get())) {
                        notACKedCondition.await();
                    }
                    Map.Entry<Integer, Segment> entry = segmentStream.pollFirstEntry();
                    NotACKedSegment notACKedSegment = new NotACKedSegment(entry.getValue());
                    notACKedSegment.timer.schedule(notACKedSegment.task, notACKedSegment.delay);
                    notACKed.put(entry.getKey(), notACKedSegment);
                    socket.rawChannelSend(entry.getValue().toByteStream(), receiver.IP, receiver.port);
                }
                notACKedLock.unlock();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class ACKReceiver implements Runnable {
        @Override
        public void run() {
            // Transmission
            while (!segmentStream.isEmpty() || !notACKed.isEmpty()) {
                Segment segment = FilteredSocket.datagramPacket2Segment(socket.receive());
                while (segment == null) {
                    segment = FilteredSocket.datagramPacket2Segment(socket.receive());
                }
                notACKedLock.lock();
                int ack = segment.ackNum;
                int sack = segment.sackNum;
                int rcvWnd = segment.rcvWnd;
                RCV_WND.set(rcvWnd);
                assert (notACKed.containsKey(ack) && notACKed.containsKey(sack));
                notACKed.remove(sack);
                Map.Entry<Integer, NotACKedSegment> entry = notACKed.pollFirstEntry();
                while (entry.getKey() < ack) {
                    entry = notACKed.pollFirstEntry();
                }
                notACKedCondition.signal();
                notACKedLock.unlock();
            }

        }
    }

    private static void rdt() {
        Thread mainSender = new Thread(new MainSender());
        Thread ackReceiver = new Thread(new ACKReceiver());
        mainSender.start();
        ackReceiver.start();
    }
}
