import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Timer;

/**
 * FastFtp Class
 * 
 * FastFtp implements a basic FTP application based on UDP data transmission.
 * The main mehtod is send() which takes a file name as input argument and send
 * the file to the specified destination host.
 * 
 * Some print statements were intentionally left  as comments in the event that further testing is required
 */
public class FastFtp {

	private DataInputStream inputStream;
	private DataOutputStream outStream;
	private Socket tcpSocket;
	private int winSize;
	private int timeInterval;
	public TxQueue window;
	private DatagramSocket udpSocket;
	private InetAddress ipAddress;
	private Timer time;
	
	/**
	 * Constructor to initialize the program
	 * 
	 * @param windowSize
	 *            Size of the window for Go-Back_N (in segments)
	 * @param rtoTimer
	 *            The time-out interval for the retransmission timer (in
	 *            milli-seconds)
	 */
	public FastFtp(int windowSize, int rtoTimer) {
		winSize = windowSize;
		timeInterval = rtoTimer;
	}

	/**
	 * Sends the specified file to the specified destination host: 1. send file
	 * name and receiver server confirmation over TCP 2. send file segment by
	 * segment over UDP 3. send end of transmission over tcp 3. clean up
	 * 
	 * @param serverName
	 *            Name of the remote server
	 * @param serverPort
	 *            Port number of the remote server
	 * @param fileName
	 *            Name of the file to be transferred to the remote server
	 * @throws InterruptedException
	 */
	public  void send(String serverName, int serverPort, String fileName)
			throws InterruptedException {

		try {

			// complete TCP handshake
			tcpSocket = new Socket(serverName, serverPort);
			int locPort = tcpSocket.getLocalPort();
			outStream = new DataOutputStream(tcpSocket.getOutputStream());
			inputStream = new DataInputStream(tcpSocket.getInputStream());

			// send filename
			outStream.writeUTF(fileName);

			int count = 10; // used to verify TCP handshake
			count = inputStream.readByte(); // response from TCP server

			if (count != 0) {
				System.out.println("There is a problem with TCP response!");
			}

			else {
				// Initializez the udpSocket
				udpSocket = new DatagramSocket(locPort);

				// get the ipAddress of the server
				ipAddress = InetAddress.getByName(serverName);
				window = new TxQueue(winSize);
				
				// start the recieveCK thread
				ACKRecieve ackRec = new ACKRecieve(udpSocket, this);
				ackRec.start();
							
				// read the file into a byte array
				File f = new File(fileName);
				FileInputStream inFile;				
				byte [] buf = new byte[Segment.MAX_PAYLOAD_SIZE];
				int byteCount = 0;
				int seqNum = 0;
				try {
					inFile = new FileInputStream(f);
					
					// read piece of the file into byte array/break the file into segments
					while ((byteCount = inFile.read(buf)) > 0) {
						byte[] tempBuf = Arrays.copyOf(buf,byteCount);
						Segment tempSeg = new Segment(seqNum++,tempBuf);
						while(window.isFull()){Thread.yield();}
						processSend(tempSeg);
					}							
					inFile.close();
					
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
				
				//System.out.println("Has Ended");// Test Message

				while (!(window.isEmpty())) {
					Thread.yield();
				}
				time.cancel();	
				//terminate the Receive thread
				ackRec.terminate();
				ackRec.join(10);
				
				//close the TCP connections
				outStream.writeByte(0);				
				outStream.close();
				inputStream.close();
				
				//close the UDP connection
				udpSocket.close();
				
			}
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * 
	 * @return returns FastFtp's time interval
	 */
	public int getTimeInterval() {
		return timeInterval;
	}

	/**
	 * 
	 * @return - returns FastFtp's timer 
	 *      
	 */
	public Timer getTimer() {
		return time;
	}

	/**
	 * Sets the global timer object
	 * @param t
	 *       the new Timer
	 */
	public void setTimer(Timer t) {
		time = t;
	}

    /**
     * Sends a segment through the UDP protocol to a server
     * @param seg
     *        the segment to be sent
     * @throws InterruptedException
     * @throws IOException
     */
	public synchronized void processSend(Segment seg)
			throws InterruptedException, IOException {
		// send seg to the UDP socket
		byte[] bytes = seg.getBytes();

		udpSocket.send(new DatagramPacket(bytes, bytes.length, ipAddress,
				tcpSocket.getPort()));

		// add seg to the transmission queue txQueue
		window.add(seg);

		// if txQueue.size() == 1, start timer
		if (window.size() == 1) {
			//System.out.println("Start timer");
			time = new Timer();
			time.schedule(new TimeoutHandler(this), timeInterval);
		}
	}
	
    /**
     * Method processes a segment
     * @param ack
     * @throws InterruptedException
     */
	public synchronized void processACK(Segment ack)
			throws InterruptedException {
		int size = window.toArray().length;
		//System.out.println("WIndow Size: " +size);
		
		if (size > 0) {
			//System.out.println("First Sequence #: " + window.element().getSeqNum());
			Segment[] arr = window.toArray();
			int startSeg = arr[0].getSeqNum();
			int endSeg = arr[arr.length - 1].getSeqNum();
			//System.out.println("First Sequence #: " + startSeg);
			//System.out.println("Last Sequence #: " + endSeg);
			
			int currentSeg = ack.getSeqNum();
			//System.out.println("Current ack: " + currentSeg );
			if ((currentSeg >= startSeg) && (currentSeg <= endSeg + 1)) {
				time.cancel();

				while ((window.element() != null)
						&& (window.element().getSeqNum() < ack.getSeqNum())) {
					//System.out.println("removing");
					window.remove();
				}
				//System.out.println("testy");
				if (!(window.isEmpty())) {
					
					time = new Timer();
					time.schedule(new TimeoutHandler(this),
							timeInterval);
				}
			}
		}
		//else{return;}
	}

	/**
	 * Method to handle a timeout, if a timeout occurs this method will
	 * re-send all segments that remain in a the window
	 * @throws IOException
	 */
	public synchronized void processTimeout() throws IOException {
		// get the list of all pending segments by calling txQueue.toArray ( )
		Segment[] remainingSegs = window.toArray();
		//System.out.println("Timeout Occured");
		
		// go through the list and send all segments to the UDP socket
		for (int i = 0; i < remainingSegs.length; i++) {
			Segment seg = remainingSegs[i];
			byte[] bytes = seg.getBytes();			
			udpSocket.send(new DatagramPacket(seg.getBytes(), bytes.length,
					ipAddress, tcpSocket.getPort()));			
		}
		
		// if not txQueue . isEmpty ( ) , start the timer
		if (!(window.isEmpty())) {
			 time = new Timer();
			time.schedule(new TimeoutHandler(this), timeInterval);
		}
	}

	/**
	 * A simple test driver
	 * 
	 * @throws InterruptedException
	 * 
	 */
	public static void main(String[] args) throws InterruptedException {
		int windowSize = 10; // segments
		int timeout = 10; // milli-seconds

		String serverName = "localhost";
		String fileName = "";
		int serverPort = 0;

		// check for command line arguments
		if (args.length == 3) {
			// either privide 3 paramaters
			serverName = args[0];
			serverPort = Integer.parseInt(args[1]);
			fileName = args[2];
		} else if (args.length == 2) {
			// or just server port and file name
			serverPort = Integer.parseInt(args[0]);
			fileName = args[1];
		} else {
			System.out.println("wrong number of arguments, try again.");
			System.out.println("usage: java FastFtp server port file");
			System.exit(0);
		}

		FastFtp ftp = new FastFtp(windowSize, timeout);

		System.out.printf("sending file \'%s\' to server...\n", fileName);
		ftp.send(serverName, serverPort, fileName);
		System.out.println("file transfer completed.");
		System.exit(0);
	}

}
