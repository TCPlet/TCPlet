import java.nio.ByteBuffer;

public class Segment {
    /* Header */
    public int seqNum;
    public int ackNum;
    public int sackNum;
    public int rcvWnd;

    public boolean ack;
    public boolean syn;
    public boolean fin;

    /* Data */
    public byte[] data;

    public byte[] toByteStream() {
        ByteBuffer buffer = ByteBuffer.allocate(20 + data.length); // 20 bytes for the given fields
        buffer.putInt(seqNum);
        buffer.putInt(ackNum);
        buffer.putInt(sackNum);
        buffer.putInt(rcvWnd);
        short flagsAndPadding = (short) (((ack ? 1 : 0) << 2) | ((syn ? 1 : 0) << 1) | (fin ? 1 : 0));
        buffer.putShort(flagsAndPadding);
        buffer.putShort((short) 0);
        buffer.put(data);
        byte[] checksum = Checksum.genChecksum(buffer.array());
        assert (checksum != null && checksum.length == 2);//checksum为16位，两字节

        buffer = ByteBuffer.allocate(20 + data.length); // 20 bytes for the given fields
        buffer.putInt(seqNum);
        buffer.putInt(ackNum);
        buffer.putInt(sackNum);
        buffer.putInt(rcvWnd);
        buffer.putShort(flagsAndPadding);
        buffer.put(checksum);
        buffer.put(data);

        return buffer.array();
    }

    public static Segment toSegment(byte[] packet) {
        Segment segment = new Segment();

        ByteBuffer buffer = ByteBuffer.wrap(packet);

        // Retrieve the fields from the byte stream
        segment.seqNum = buffer.getInt();
        segment.ackNum = buffer.getInt();
        segment.sackNum = buffer.getInt();
        segment.rcvWnd = buffer.getInt();

        short flagsAndPadding = buffer.getShort();
        segment.ack = ((flagsAndPadding >> 2) & 1) == 1;
        segment.syn = ((flagsAndPadding >> 1) & 1) == 1;
        segment.fin = (flagsAndPadding & 1) == 1;

        // Extract the checksum
        byte[] checksum = new byte[16];
        buffer.get(checksum);

        if (!Checksum.verifyChecksum(packet)) {
            // Handle checksum validation failure
            return null;
        }

        // Extract the data
        segment.data = buffer.array();

        return segment;
    }

}
