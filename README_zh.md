# 项目：TCPlet

### *由第23组实现：*
> 宁锐（组长），周煜，张康，翁天成，陈彦昀

### 1. 介绍

我们的小组在TCPlet中实现了连接管理、可靠数据传输和流量控制功能。为了模拟L3 IP协议的***无连接***特性，我们使用了DatagramSocket而不是ServerSocket和Socket的组合作为该项目的基石。

### 2. TCPlet段头结构

| 字段                           | 大小（位）  |
|---------------------------------|-------------|
| 序列号                         | 32          |
| 确认号                         | 32          |
| 选择性确认号                   | 32          | 
| 接收窗口                       | 32          |
| 填充                            | 13          | 
| 控制标志                       | 3           | 
| 校验和                         | 16          | 

### 3. 握手

> ***client_isn和server_isn都需要随机化。***
> 
> ***SYN段占用1字节***

1. 客户端发送SYN：
> 序列号 = 随机化的client_isn
> 
> 控制标志: SYN = 1
 
2. 服务器发送SYN-ACK：
> 序列号 = 随机化的server_isn
> 
> 控制标志: ACK = 1, SYN = 1
 
3. 客户端发送ACK：（数据部分是可选的，在TCPlet中不需要）
> 控制标志: ACK = 1

![握手](https://media.geeksforgeeks.org/wp-content/uploads/TCP-connection-1.png)

### 4. 挥手

> ***FIN段占用1字节***

1. 客户端发送FIN：
> 控制标志: ACK = 1, FIN = 1

2. 服务器发送ACK：
> 控制标志: ACK = 1

3. 服务器发送FIN：
> 控制标志: ACK = 1, FIN = 1

4. 客户端发送ACK：
> 控制标志: ACK = 1
 
![挥手](http://images.timd.cn/blog/2018/tcp-four-way-wavehand.gif)

### 5. 任务

#### 重要提示：使用FilteredSocket而不是DatagramSocket

1. 实时仿真工具和错误检测：（FilteredSocket.java, Checksum.java） 张康
   > 1. 模拟超时、有损、损坏、乱序的段
2. 连接管理:
    * 握手（Handshake.java） 翁天成
        > 1. 3次握手（与TCP相同）：SYN、SYN-ACK、ACK
    * 挥手（Wavehand.java） 陈彦昀
        > 1. 4次挥手（与TCP相同）：ACK-FIN、ACK、ACK-FIN、ACK
3. 可靠数据传输:
    * 发送方（TCPSender.java） 宁锐
        > 1. Go-Back-N ARQ：对于***ackNum***字段，已经确认的段的seqNum < ackNum。
        > 2. Selective Repeat ARQ：对于***sackNum***字段，已经确认的段的seqNum = sackNum。
        > 3. 流量控制：***rcvWnd***字段表示接收方的空闲缓冲区大小。
    * 接收方（TCPReceiver.java） 周煜
        > 1. Go-Back-N ARQ：对于***ackNum***字段，已经确认的段的seqNum < ackNum。
        > 2. Selective Repeat ARQ：对于***sackNum***字段，已经确认的段的seqNum = sackNum。
        > 3. 流量控制：***rcvWnd***字段表示接收方的空闲缓冲区大小。

### 6. TCPlet协议实现详细信息

1. 连接管理
   1. 3次握手：如果在传输过程中ack超时，则等待并重新发送（在重发时验证序列号以防重复段）。
   2. 4次挥手：如果在传输过程中ack超时，则等待并重新发送（在重发时验证序列号以防重复段）。
2. 可靠数据传输
   1. 序列号和确认号
      1. 序列号 = 数据中第一个字节的字节流编号（我们定义SYN、SYN-ACK、不带数据的FIN段的数据长度为1）
      2. 确认号 = 从发送方期望的段的序列号 ***重要：（累积确认号）***
      3. 选择性确认号 = 本次接收的段的序列号。
   2. 自动重复请求
      1. 快速重传：3个重复的确认号
      2. 超时重传：（RTO = 估计的RTT + 4 * DevRTT）
3. 流量控制
   1. 接收窗口 = 接收器缓冲区大小 - （已确认的最后一个字节 - 已处理的最后一个字节）
   2. 发送方保证在FlightSegments（已发送的最后一个字节 - 已确认的最后一个字节）<= 接收窗口
4. 仿真:
   1. 乱序实现需要在套接字中进行缓冲。
   2. 数据的损坏部分不应太大。（例如，0.1%）
   3. 要模拟数据丢失，只需什么都不做
5. 校验和:
   1. 与TCP相同，将每2个字节相加。

### 7. 使用方法

1. 编译：在src/main/java下执行javac *.class
2. 执行：
    * ***首先运行TCPSender***：java TCPSender -p 50000(可随机指定未使用的端口) -f input.txt(可换为别的文件)
    * 运行TCPReceiver：java TCPReceiver -s 127.0.0.1 -p 50000(可随机指定未使用的端口)
3. 检验：TCPReceiver将收到的文件输出为output.txt，diff output.txt input.txt即可检验

注：MSS可在TCPSender.java内设置，默认为1460
