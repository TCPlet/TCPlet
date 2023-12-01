# *Project: TCPlet*

### *Implemented by Group 23:*
> 宁锐 (leader) , 周煜, 张康, 翁天成, 陈彦昀

### 1. Introduction

Our group implemented Connection Management, Reliable Data Transfer and Flow Control function in TCPlet. To simulate the ***connectionless*** feature of L3 IP protocol, we use DatagramSocket instead of the combination of ServerSocket and Socket as the cornerstone of this project.

### 2. TCPlet Segment Header Structure

| Field                           | Size (bits) |
|---------------------------------|-------------|
| Sequence Number                 | 32          |
| Acknowledgment Number           | 32          |
| Receive Window                  | 32          |
| Selective Acknowledgment Number | 32          | 
| Padding                         | 13          | 
| Control Flags                   | 3           | 
| Checksum                        | 16          | 

### 3. Handshake

> ***Both client_isn and server_isn need randomizing.***

1. Client sends SYN:
> Sequence Number = randomized client_isn
> 
> Control Flags: SYN = 1
 
2. Server sends ACK:
> Sequence Number = randomized server_isn
> 
> Control Flags: ACK = 1, SYN = 1
 
3. Client sends ACK: (Data part is optional, not needed in TCPlet)
> Control Flags: ACK = 1

[//]: # (![Handshake]&#40;https://media.geeksforgeeks.org/wp-content/uploads/TCP-connection-1.png&#41;)

### 4. Wavehand

1. Client sends FIN:
> Control Flags: ACK = 1, FIN = 1

2. Server sends ACK:
> Control Flags: ACK = 1

3. Server sends FIN:
> Control Flags: ACK = 1, FIN = 1

4. Client sends ACK:
> Control Flags: ACK = 1
 
[//]: # (![Handshake]&#40;http://images.timd.cn/blog/2018/tcp-four-way-wavehand.gif&#41;)

### 5. Sequence Number and Acknowledgment Number

1. Sequence Number = the byte stream number of the first byte in data
2. Acknowledgment Number = sequence number of expecting segment from sender

### 6. Tasks

1. Real-time Simulation Utilities and Error Detection: (FilterSocket.java, Checksum.java) 张康
   > 1. Simulate timeout, lossy, corrupted, out-of-order segments
2. Connection Management:
    * Handshake (Handshake.java) 翁天成
        > 1. 3-way handshake
    * Wavehand (Wavehand.java) 陈彦昀
        > 1. 4-way wavehand
3. Reliable Data Transfer(ARQ):
    * Sender (TCPSender.java) 宁锐
        > 1. GBN + SACK
        > 2. Flow Control
    * Receiver (TCPReceiver.java) 周煜
        > 1. GBN + SACK
