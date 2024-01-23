import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
                    if (debug) {
                        System.out.printf("Timeout send: ack %d, len %d\n", ackNum, segment.data.length);
                        StringBuilder sb = new StringBuilder();
                        for (int i : inFlight.keySet()) {
                            sb.append(i + " ");
                        }
                        System.out.println("In flight ackNum: " + sb);
                    }
                    send(segment);
                    timer.schedule(new TimeoutTask(), delay);
                    if (delay < 1000) {
                        delay *= 2;
                    }
                } else {
                    timer.cancel();
                    Thread.currentThread().interrupt();
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
    public static final int SND_WND = 16 * 1024;
    public FilteredSocket socket;
    public ReceiverInfo receiver;
    private AtomicInteger RCV_WND;
    private final TreeMap<Integer, Segment> segmentStream = new TreeMap<>();
    // <ackNum, Segment>
    private final TreeMap<Integer, InFlightSegment> inFlight = new TreeMap<>();

    private long EstimatedRTT = 1000;
    private long DevRTT = 0;
    private int prevACK = 0;
    private int duplicateACKCount = 0;
    private final Lock inFlightLock = new ReentrantLock();
    private final Condition inFlightCondition = inFlightLock.newCondition();

    private static boolean debug = false;

    /**
     * @param args java TCPSender -p SENDER_PORT -f FILE_NAME -d
     */
    public static void main(String[] args) {
        int senderPort = Integer.parseInt(args[1]);
        File file = new File(args[3]);
        if (args.length > 4) {
            debug = true;
        }
        byte[] data = new byte[0];
        try {
            data = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
            if (debug) {
                System.out.printf("Segments: seq: %d, ack: %d\n", prevSeq, prevSeq + end - start);
            }
            System.arraycopy(data, start, arr, 0, end - start);
            Segment segment = new Segment();
            segment.seqNum = prevSeq;
            segment.data = arr;
            segmentStream.put(segment.seqNum, segment);
            prevSeq += end - start;
        }
        rdt();
        Wavehand.senderClose(socket, seqNum + len, receiver.IP, receiver.port);
        System.exit(0);
    }

    public void send(Segment segment) {
        socket.noisyAndLossyChannelSend(segment.toByteStream(), receiver.IP, receiver.port);
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
                    if (inFlight.size() * MSS >= RCV_WND.get()) {
                        inFlightCondition.await();
                    }
                    Map.Entry<Integer, Segment> entry = segmentStream.pollFirstEntry();
                    while (inFlight.isEmpty() && RCV_WND.get() < entry.getValue().data.length) {
                        Segment zeroProbingSegment = new Segment();
                        zeroProbingSegment.data = new byte[0];
                        zeroProbingSegment.seqNum = prevACK;
                        send(zeroProbingSegment);
                        if (debug) {
                            System.out.println("Zero window probing");
                        }
                        Thread.sleep(EstimatedRTT + 4 * DevRTT);
                    }
                    InFlightSegment inFlightSegment = new InFlightSegment(entry.getValue());
                    inFlight.put(entry.getKey() + inFlightSegment.segment.data.length, inFlightSegment);
                    send(entry.getValue());
                    if (debug) {
                        System.out.printf("DATA sent: seq %d, len %d, ack %d\n", entry.getValue().seqNum, entry.getValue().data.length, entry.getValue().seqNum + entry.getValue().data.length);
                    }
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
                if (debug) {
                    System.out.printf("ACK received: ack %d sack %d wnd %d\n", ack, segment.sackNum, segment.rcvWnd);
                }
                //TODO: DEBUG
                inFlightLock.lock();
                if (prevACK == ack) {
                    if (++duplicateACKCount >= 3) {
                        for (Map.Entry<Integer, InFlightSegment> e : inFlight.entrySet()) {
                            InFlightSegment inFlightSegment = e.getValue();
                            inFlightSegment.retransmitted = true;
                            if (debug) {
                                System.out.printf("FAST retransmit: seq %d, len %d\n", inFlightSegment.segment.seqNum, inFlightSegment.segment.data.length);
                            }
                            send(inFlightSegment.segment);
                            duplicateACKCount = 0;
                        }
                    }
                } else {
                    prevACK = ack;
                    duplicateACKCount = 0;
                }
                int sack = segment.sackNum;
                RCV_WND.set(segment.rcvWnd);
                // TODO: For debug
                assert (inFlight.containsKey(ack) && inFlight.containsKey(sack));
                Map.Entry<Integer, InFlightSegment> entry = inFlight.firstEntry();
                long curTimeStamp = System.currentTimeMillis();
                while (!inFlight.isEmpty() && entry.getKey() <= ack) {
                    inFlight.pollFirstEntry();
                    if (debug) {
                        System.out.printf("Remove ack: %d\n", entry.getValue().ackNum);
                    }
                    if (!entry.getValue().retransmitted) {
                        updateRTT(curTimeStamp - entry.getValue().timestamp);
                    }
                    entry = inFlight.firstEntry();
                }
                inFlight.remove(sack);
                inFlightCondition.signal();
                inFlightLock.unlock();
            }
            System.out.println("All Empty!");
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
