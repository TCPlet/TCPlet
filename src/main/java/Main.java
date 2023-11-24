
public class Main {
    /**
     * @param args
     * SYNOPSIS:
     * java Main -m s -w WND_SIZE -p SENDER_PORT -s MSS
     * java Main -m c -s SENDER_IP_ADDR -p SENDER_PORT
     */
    public static void main(String[] args) {

        // sender模式(-m s): 使用 在成功建立连接之后, 读取命令行中的输入并传递给TCPSender类中的senderLoop
        // receiver模式(-m r): 在成功建立连接之后, 读取命令行中的输入并传递给TCPReceiver类中的ReceiverLoop
        // 无论哪种模式下，捕获Ctrl-C(SIGINT)并使用Wavehand终止连接
    }
}
