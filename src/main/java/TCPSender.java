import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TCPSender {
    private class InFlightSegment {
        Segment segment;
        long timestamp = System.currentTimeMillis();
        boolean retransmitted = false;
        long delay = EstimatedRTT + 4 * DevRTT;
        int ackNum;
        Timer timer = new Timer();

        class TimeoutTask extends TimerTask {
            @Override
            public void run() {
                if (inFlight.containsKey(ackNum)) {
                    retransmitted = true;
                    send(segment);
                    timer.schedule(new TimeoutTask(), delay);
                    if (delay < 60000) {
                        delay *= 2;
                    }
                }

            }
        }

        InFlightSegment(Segment segment) {
            this.segment = segment;
            ackNum = segment.seqNum + segment.data.length;
            timer.schedule(new TimeoutTask(), delay);
        }
    }

    // Default Maximum Segment Size = 1460B (MSS = MTU - 40B[TCP Header + IP Header])
    public static final int MSS = 1460;

    // Default Send Window Size = 2048B
    public static final int SND_WND = 2048;
    public FilteredSocket socket;
    public ReceiverInfo receiver;
    private AtomicInteger RCV_WND;
    // <ackNum, Segment>
    private final TreeMap<Integer, Segment> segmentStream = new TreeMap<>();
    private final TreeMap<Integer, InFlightSegment> inFlight = new TreeMap<>();

    private long EstimatedRTT = 1000;
    private long DevRTT = 0;
    private int prevACK = 0;
    private int duplicateACKCount = 0;
    private final Lock inFlightLock = new ReentrantLock();
    private final Condition inFlightCondition = inFlightLock.newCondition();

    /**
     * @param args java TCPSender -p SENDER_PORT -d DATA
     */
    public static void main(String[] args) {
        int senderPort = Integer.parseInt(args[1]);
        byte[] data = args[3].getBytes();
        FilteredSocket socket = new FilteredSocket(senderPort);
        ReceiverInfo receiver = Handshake.accept(socket);
        TCPSender sender = new TCPSender(socket, Objects.requireNonNull(receiver));
        sender.send(data);
    }

    public TCPSender(FilteredSocket socket, ReceiverInfo receiver) {
        this.socket = socket;
        this.receiver = receiver;
        RCV_WND = new AtomicInteger(receiver.rcvWnd);
    }

    public void send(byte[] data) {
        int seqNum = receiver.seq;
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

    public void send(Segment segment) {
        socket.rawChannelSend(segment.toByteStream(), receiver.IP, receiver.port);
    }


    class MainSender implements Runnable {
        @Override
        public void run() {
            try {
                // Event:
                // 1. RCV_WND && SND_WND: lastSent - lastACK >= min(SND_WND, RCV_WND)
                // 2. ACK received: window moving forward
                // 3. Timeout Retransmit
                // 4. Fast Retransmit
                while (!segmentStream.isEmpty()) {
                    inFlightLock.lock();
                    // If there's in flight segment, there will be a ack from receiver eventually.
                    if (inFlight.size() * MSS >= SND_WND) {
                        inFlightCondition.await();
                    }
                    while (inFlight.size() * MSS >= RCV_WND.get() && RCV_WND.get() != 0) {
                        inFlightCondition.await();
                    }
                    while (RCV_WND.get() == 0) {
                        Segment zeroProbingSegment = new Segment();
                        zeroProbingSegment.data = new byte[0];
                        zeroProbingSegment.seqNum = prevACK;
                        send(zeroProbingSegment);
                    }
                    Map.Entry<Integer, Segment> entry = segmentStream.pollFirstEntry();
                    InFlightSegment inFlightSegment = new InFlightSegment(entry.getValue());
                    inFlight.put(entry.getKey(), inFlightSegment);
                    send(entry.getValue());
                    inFlightLock.unlock();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    class ACKReceiver implements Runnable {
        @Override
        public void run() {
            // Transmission
            while (!segmentStream.isEmpty() || !inFlight.isEmpty()) {
                Segment segment = FilteredSocket.datagramPacket2Segment(socket.receive());
                while (segment == null) { // ensure receive segment with accurate content
                    segment = FilteredSocket.datagramPacket2Segment(socket.receive());
                }
                int ack = segment.ackNum;
                if (prevACK == ack) {
                    if (++duplicateACKCount >= 3) {
                        InFlightSegment inFlightSegment = inFlight.firstEntry().getValue();
                        inFlightSegment.retransmitted = true;
                        send(inFlightSegment.segment);
                    }
                } else {
                    prevACK = ack;
                    duplicateACKCount = 0;
                }
                int sack = segment.sackNum;
                inFlightLock.lock();
                RCV_WND.set(segment.rcvWnd);
                // TODO: For debug
                assert (inFlight.containsKey(ack) && inFlight.containsKey(sack));
                inFlight.remove(sack);
                Map.Entry<Integer, InFlightSegment> entry;
                long curTimeStamp = System.currentTimeMillis();
                do {
                    entry = inFlight.pollFirstEntry();
                    if (!entry.getValue().retransmitted) {
                        updateRTT(curTimeStamp - entry.getValue().timestamp);
                    }
                } while (entry.getKey() < ack);
                inFlightCondition.signal();
                inFlightLock.unlock();
            }

        }
    }

    private void updateRTT(long SampleRTT) {
        long newEstimatedRTT = (long) (0.875 * EstimatedRTT + 0.125 * SampleRTT);
        DevRTT = (long) (0.75 * DevRTT + 0.25 * Math.abs(EstimatedRTT - newEstimatedRTT));
        EstimatedRTT = newEstimatedRTT;
    }

    private void rdt() {
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
