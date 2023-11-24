public class Segment {
    /* Header */
    public int seqNum;
    public int ackNum;

    public boolean urg;
    public boolean ack;
    public boolean psh;
    public boolean rst;
    public boolean syn;
    public boolean fin;

    /* Data */
    public String data;

    public byte[] toByteStream() {
        // TODO
        return null;
    }

    public static Segment toSegment(byte[] packet) {
        // TODO
        return null;
    }
}
