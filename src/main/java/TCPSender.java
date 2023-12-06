import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TCPSender {
    private static class NotACKedSegment {
        Segment segment;
        long timestamp = System.currentTimeMillis();
        boolean retransmitted = false;
        long delay = EstimatedRTT + 4 * DevRTT;
        int ackNum;
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                while (notACKed.containsKey(ackNum)) {
                    retransmitted = true;
                    send(segment);
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
    public static final int SND_WND = 2048;
    public static FilteredSocket socket;
    public static ReceiverInfo receiver;
    private static AtomicInteger RCV_WND;
    // <ackNum, Segment>
    private static final TreeMap<Integer, Segment> segmentStream = new TreeMap<>();
    private static final TreeMap<Integer, NotACKedSegment> notACKed = new TreeMap<>();

    private static long EstimatedRTT = 1000;
    private static long DevRTT = 0;
    private static int prevACK = 0;
    private static int duplicateACKCount = 0;
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

    public static void send(Segment segment) {
        socket.rawChannelSend(segment.toByteStream(), receiver.IP, receiver.port);
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
                while (!segmentStream.isEmpty()) {
                    notACKedLock.lock();
                    if (notACKed.size() * MSS >= SND_WND) {
                        notACKedCondition.await();
                    }
                    while (notACKed.size() * MSS >= RCV_WND.get()) {
                        Segment zeroProbing = new Segment();
                        zeroProbing.data = new byte[0];
                        zeroProbing.seqNum = prevACK - 1;
                        send(zeroProbing);
                    }
                    Map.Entry<Integer, Segment> entry = segmentStream.pollFirstEntry();
                    NotACKedSegment notACKedSegment = new NotACKedSegment(entry.getValue());
                    notACKedSegment.timer.schedule(notACKedSegment.task, notACKedSegment.delay);
                    notACKed.put(entry.getKey(), notACKedSegment);
                    send(entry.getValue());
                    notACKedLock.unlock();
                }
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
                int ack = segment.ackNum;
                if (prevACK == ack) {
                    if (++duplicateACKCount >= 3) {
                        NotACKedSegment notACKedSegment = notACKed.firstEntry().getValue();
                        notACKedSegment.retransmitted = true;
                        send(notACKedSegment.segment);
                    }
                } else {
                    prevACK = ack;
                    duplicateACKCount = 0;
                }
                int sack = segment.sackNum;
                int rcvWnd = segment.rcvWnd;
                notACKedLock.lock();
                RCV_WND.set(rcvWnd);
                assert (notACKed.containsKey(ack) && notACKed.containsKey(sack));
                notACKed.remove(sack);
                Map.Entry<Integer, NotACKedSegment> entry;
                long curTimeStamp = System.currentTimeMillis();
                do {
                    entry = notACKed.pollFirstEntry();
                    if (!entry.getValue().retransmitted) {
                        updateRTO(curTimeStamp - entry.getValue().timestamp);
                    }
                } while (entry.getKey() < ack);
                notACKedCondition.signal();
                notACKedLock.unlock();
            }

        }
    }

    private static void updateRTO(long SampleRTT) {
        long newEstimatedRTT = (long) (0.875 * EstimatedRTT + 0.125 * SampleRTT);
        DevRTT = (long) (0.75 * DevRTT + 0.25 * Math.abs(EstimatedRTT - newEstimatedRTT));
        EstimatedRTT = newEstimatedRTT;
    }

    private static void rdt() {
        Thread mainSender = new Thread(new MainSender());
        Thread ackReceiver = new Thread(new ACKReceiver());
        mainSender.start();
        ackReceiver.start();
        try {
            mainSender.join();
            ackReceiver.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
