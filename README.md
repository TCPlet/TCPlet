# *Project: TCPlet*

### *Implemented by Group 23:*
> 宁锐 (leader) , 周煜, 张康, 翁天成, 陈彦昀

### 1. Introduction

To simulate the ***connectionless*** feature of L3 IP protocol, We use DatagramSocket instead of the combination of ServerSocket and Socket as the cornerstone of this project.

### 2. Segment Header Structure

| Field                           | Size (bits) | Description                                                       |
|---------------------------------|-------------|-------------------------------------------------------------------|
| Source Port             | 16          | Identifies the sender's application port. |
| Destination Port        | 16          | Identifies the receiver's application port. |
| Sequence Number                 | 32          | Sequence number to identify the order of TCP segments.            |
| Acknowledgment Number           | 32          | Cumulative Acknowledgment number; present if ACK flag is set.     |
| Reserved                        | 10          | Reserved for future use. Should be set to zero.                   |
| Control Flags                   | 6           | Flags indicating the purpose of the TCP segment; SYN flag is set. |
| Checksum                        | 16          | Checksum for error detection.                                     |
| Selective Acknowledgment Number | 32          | SACK number; present if ACK flag is set.                          |

### 3. Handshake

> ***Both Client Seq and Server Seq need randomizing.***

1. Client sends SYN:
> seqNum: randomized client_isn
> 
> Control Flags: SYN = 1
 
2. Server sends ACK:
> seqNum: randomized server_isn
> 
> Control Flags: ACK = 1, SYN = 1
 
3. Client sends ACK: (Data part optional, not needed in TCPlet)
> Control Flags: ACK = 1

![Handshake](https://media.geeksforgeeks.org/wp-content/uploads/TCP-connection-1.png)

### 4. Wavehand

1. Client sends FIN:
> CtrFlags: ACK = 1, FIN = 1

2. Server sends ACK:
> CtrFlags: ACK = 1

3. Server sends FIN:
> CtrFlags: ACK = 1, FIN = 1

4. Client sends ACK:
> CtrFlags: ACK = 1
 
![Handshake](http://images.timd.cn/blog/2018/tcp-four-way-wavehand.gif)

### 5. Formula for ***Sequence Number*** and ***Acknowledgment Number***:

1. Sequence Number = SeqNum of last ***sent*** segment + length of data sent this time (If no data, then length of data = 1)
2. Acknowledgment Number = SeqNum of last ***received*** segment + length of data sent this time (If no data, then length of data = 1)

### 6. Tasks:

1. Application Layer Data Preprocess: (Main.java) 张康
    > 1. Data Fragmentation (MSS setting)
    > 2. Main entry
2. Real-time Simulation Utilities and Error Detection: (Filter.java, Checksum.java) 张康
   > 1. Simulate Timeout, Lossy, Corrupted, Out-of-order
3. Connection Management:
    * Handshake 陈彦昀
        > 1. 3-way Handshake
        > 2. Segment loss during connection establishment
    * Wavehand 翁天成
        > 1. 4-way Wavehand
        > 2. Segment loss during connection establishment
4. Reliable Data Transfer(ARQ): SACK Protocol
    * Sender (TCPSender.java) 宁锐
        > 1. SACK Protocol
    * Receiver (TCPReceiver.java) 周煜
        > 1. SACK Protocol
