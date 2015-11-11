import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.Timer;

/**
 * FastFtp Class
 * 
 * FastFtp implements a basic FTP application based on UDP data transmission.
 * The main mehtod is send() which takes a file name as input argument and send
 * the file to the specified destination host.
 * 
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
	private int serverPort;
	private Timer time;
	private TimeoutHandler timeHandler;

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
			int i = 0;

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
				ACKRecieve ackRec = new ACKRecieve(udpSocket, locPort,
						serverName, window, this);
				ackRec.start();

				// turn the file into an ArrayList of segements
				ArrayList<Segment> sendFile = Utils.fileToSegments(fileName);

				// test writing the file

				String testFile = "uctest.gif";
				// concatenate the file path
				String dir = System.getProperty("user.dir");
				String separator = System.getProperty("file.separator");
				String absPath = dir + separator;

				// TODO remove the 5 lines of code below once testing is done
				// used for testing purposes
				File outFile = new File(absPath, testFile);
				outFile.getParentFile().mkdirs();
				outFile.createNewFile();
				OutputStream out = new FileOutputStream(
						outFile.getAbsoluteFile());
				byte[] outBuf = new byte[1000];
				// end of code to be removed

				while (sendFile.size() > 0) {
					Segment tempSeg = new Segment();
					tempSeg = sendFile.remove(0);
					while (window.isFull()) {
						// Thread.currentThread().wait();
						Thread.yield();

					}
					// if(window.size()==winSize){Thread.currentThread().wait();}
					processSend(tempSeg);
					Thread.yield();
					// TODO remove the 3 lines of code below, once testing is
					// done
					outBuf = tempSeg.getPayload();
					out.write(outBuf);
					out.flush();
					// end file test
				}
				System.out.println("Has Ended");// Test Message

				while (!(window.isEmpty())) {
					Thread.yield();
				}
				System.out.println("Window Empty");
				ackRec.terminate();
				ackRec.join();
				System.out.println("Joined");

				outStream.writeByte(0);
				time.cancel();

				System.out.println("Join");
				udpSocket.close();
				outStream.close();
				inputStream.close();
				System.out.println("Ended");

			}
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public int getTimeInterval() {
		return timeInterval;
	}

	public Timer getTimer() {
		return time;
	}

	public void setTimer(Timer t) {
		time = t;
	}

	public synchronized void processSend(Segment seg)
			throws InterruptedException, IOException {
		// send seg to the UDP socket
		byte[] bytes = seg.getBytes();

		udpSocket.send(new DatagramPacket(bytes, bytes.length, ipAddress,
				tcpSocket.getPort()));

		// add seg to the trnsmission queue txQueue
		window.add(seg);

		// if txQueue.size() == 1, start timer
		if (window.size() == 1) {
			System.out.println("Start timer");
			time = new Timer();
			time.schedule(new TimeoutHandler(time, this), timeInterval);
		}
	}

	public synchronized void processACK(Segment ack)
			throws InterruptedException {
		int size = window.size();
		System.out.println(size);
		
		if (size > 0) {
			System.out.println(window.element().getSeqNum());
			Segment[] arr = window.toArray();
			int startSeg = arr[0].getSeqNum();
			int endSeg = arr[arr.length - 1].getSeqNum();
			int currentSeg = ack.getSeqNum();
			if ((currentSeg > startSeg) && (currentSeg <= endSeg + 1)) {
				time.cancel();

				while ((window.element() != null)
						&& (window.element().getSeqNum() <= ack.getSeqNum())) {
					System.out.println("removing");

					window.remove();
				}
				if (!(window.isEmpty())) {
					time = new Timer();
					time.schedule(new TimeoutHandler(getTimer(), this),
							timeInterval);
				}
			}
		}
	}

	// while txQueue . element ( ) . getSeqNum( ) < ack.getSeqNum( )
	// txQueue . remove ( )
	// i f not txQueue . isEmpty ( ) , start the timer

	public synchronized void processTimeout() throws IOException {
		// get the list of all pending segments by calling txQueue.toArray ( )
		Segment[] remainingSegs = window.toArray();
		// go through the list and send all segments to the UDP socket
		for (int i = 0; i < remainingSegs.length; i++) {
			Segment seg = remainingSegs[i];
			byte[] bytes = seg.getBytes();
			System.out.println("Timeout Occured");
			udpSocket.send(new DatagramPacket(seg.getBytes(), bytes.length,
					ipAddress, tcpSocket.getPort()));
		}
		// if not txQueue . isEmpty ( ) , start the timer
		if (!(window.isEmpty())) {
			 time = new Timer();
			time.schedule(new TimeoutHandler(time, this), timeInterval);
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
		int timeout = 50; // milli-seconds

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
