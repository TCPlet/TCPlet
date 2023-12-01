
public class TCPSender {
    // Default Maximum Segment Size = 1460B
    public static int MSS = 1460;

    // Default Send Window Size = 2048B
    public static int SND_WND = 2048;
    public static FilteredSocket socket;

    /**
     * @param args
     * java TCPSender -p SENDER_PORT -d DATA
     */
    public static void main(String[] args) {
        int WND_SIZE = Integer.parseInt(args[2]);
        int SENDER_PORT = Integer.parseInt(args[4]);
        socket = new FilteredSocket(SENDER_PORT);
        //TODO
    }
}
