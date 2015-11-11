import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Timer;

public class ACKRecieve extends Thread {

	private DatagramSocket udpSocket;
	private int locPort;
	private String serverName;
	private TxQueue window;
	private FastFtp parent;
	private boolean terminated = false;

	public ACKRecieve(DatagramSocket u, int l, String s, TxQueue w, FastFtp p) {
		udpSocket = u;
		locPort = l;
		serverName = s;
		window = w;
		parent = p;
	}

	public void run() {

		while (!(terminated)) {
			byte[] recData = new byte[Segment.MAX_SEGMENT_SIZE];
			DatagramPacket recAck = new DatagramPacket(recData, recData.length);
			try {
				udpSocket.receive(recAck);
				Segment tempSeg = new Segment(recAck);
				System.out.println(tempSeg);
				parent.processACK(tempSeg);
			} catch (IOException e) {

				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			//Thread.yield();
		}
	}

	public void terminate() {
		terminated = true;
	}


}
