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

![Handshake](https://media.geeksforgeeks.org/wp-content/uploads/TCP-connection-1.png)

### 4. Wavehand

1. Client sends FIN:
> Control Flags: ACK = 1, FIN = 1

2. Server sends ACK:
> Control Flags: ACK = 1

3. Server sends FIN:
> Control Flags: ACK = 1, FIN = 1

4. Client sends ACK:
> Control Flags: ACK = 1
 
![Handshake](http://images.timd.cn/blog/2018/tcp-four-way-wavehand.gif)

### 5. Tasks

#### Important: Use FilteredSocket instead of DatagramSocket

1. Real-time Simulation Utilities and Error Detection: (FilteredSocket.java, Checksum.java) 张康
   > 1. Simulate timeout, lossy, corrupted, out-of-order segments
2. Connection Management:
    * Handshake (Handshake.java) 翁天成
        > 1. 3-way handshake
    * Wavehand (Wavehand.java) 陈彦昀
        > 1. 4-way wavehand
3. Reliable Data Transfer:
    * Sender (TCPSender.java) 宁锐
        > 1. Hybrid of GBN and SR
        > 2. Flow Control
    * Receiver (TCPReceiver.java) 周煜
        > 1. Hybrid of GBN and SR
        > 2. Flow Control

### 6. TCPlet Protocol Implementation Details

1. Connection Management
   1. 3-way handshake: if ack timeout during transmission, wait and retransmit.(Verify sequence number in case of duplicate segment)
   2. 4-way wavehand: if ack timeout during transmission, wait and retransmit.(Verify sequence number in case of duplicate segment)
2. Reliable Data Transfer
   1. Sequence Number and Acknowledgment Number
      1. Sequence Number = the byte stream number of the first byte in data (We define that SYN, SYN-ACK, FIN segment without data has data length 1)
      2. Acknowledgment Number = sequence number of expecting segment from sender ***Important: (Accumulative Acknowledgement Number)***
      3. Selective Acknowledgement Number = sequence number of the segment received this time.
   2. Automatic Repeat reQuest
      1. 3 duplicate acknowledgement number
      2. timeout (RTO = EstimatedRTT + 4 * DevRTT)
3. Flow Control
   1. Receive window = receiveBufferSize - (lastByteAcked - lastByteProcessed)
   2. Sender guarantees (lastByteSent - lastByteAcked) <= receive window
4. Simulation:
   1. Out-of-order implementation need buffering in socket.
   2. Corrupt part of data shouldn't be too large.(eg. 0.1%)
   3. To simulate data loss, just do nothing
5. Checksum:
   1. Same with TCP, add each 2 bytes together.