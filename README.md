# *Project: TCPlet*

### *Implemented by Group 23:*
> 宁锐 (leader) , 周煜, 张康, 翁天成, 陈彦昀

### 1. Introduction

To simulate the ***connectionless*** feature of L3 IP protocol, We use DatagramSocket instead of the combination of ServerSocket and Socket as the cornerstone of this project.

### 2. Segment Header Structure

| Field                   | Size (bits) | Description                               |
|-------------------------|-------------|-------------------------------------------|
| Source Port             | 16          | Identifies the sender's application port. |
| Destination Port        | 16          | Identifies the receiver's application port. |
| Sequence Number         | 32          | Sequence number to identify the order of TCP segments. |
| Acknowledgment Number   | 32          | Acknowledgment number; present if ACK flag is set. |
| Data Offset             | 4           | Length of TCP header in 32-bit words.      |
| Reserved                | 6           | Reserved for future use. Should be set to zero. |
| Control Flags           | 6           | Flags indicating the purpose of the TCP segment; SYN flag is set. |
| Window Size             | 16          | The size of the sender's receive window.  |
| Checksum                | 16          | Checksum for error detection.              |
| Urgent Pointer          | 16          | Indicates the position of urgent data in the TCP segment. |

> MTU: Maximum Transmission Unit
> 
> MSS: Maximum Segment Size
> 
> MTU = MSS + TCP Header + IP Header.

### 3. Handshake

> ***Both Client and Server Seq need randomizing.***

![Handshake](https://media.geeksforgeeks.org/wp-content/uploads/TCP-connection-1.png)

### 4. Wavehand

> ***Both Client and Server Seq need randomizing.***

![Handshake](http://images.timd.cn/blog/2018/tcp-four-way-wavehand.gif)

### 5. Files and Functions

1. *TCPReceiver.java*

```java
/** Take sender ip address and port as arguments */
public static void main(String[] args);
```

2. *TCPSender.java*

```java
/** Take sender ip address (INADDR_ANY i.e. 0.0.0.0 or ::1 as default) 
     and port as arguments */
public static void main(String[] args);
```

3. *ByteStream.java*

> Generate 5 kinds of ByteStream: ACK, SYN, RST, FIN, normal segment

Members:
```java
public byte[] data;
```

Methods:
```java
public static ByteStream ack();
public static ByteStream syn();
public static ByteStream rst();
public static ByteStream fin();

public static ByteStream data(String data);
```

4. *Segment.java*

Members:
```java
/* Header */
public int srcPort;
public int destPort;
public int seqNum;
public int ackNum;
public int headerLen;

public boolean urg;
public boolean ack;
public boolean psh;
public boolean rst;
public boolean syn;
public boolean fin;

public int winSize;
public int checksum;
public int urgPtr;

/* Data */
public String data;
```

5. *Parser.java*

> A ***tool*** class that will ***NEVER*** be instantiated.

```java
/** Taking in a ByteStream object and output a Segment object. */
public static Segment parse(ByteStream byteStream);
```
