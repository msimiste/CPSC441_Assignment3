import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
/**
 * 
 * @author Mike Simister
 * November 11, 2015
 * This Class waits for UDP acks and upon receiving them, 
 * initiates the sending of additional segments in the parent thread should they exist.
 *
 * Some print statements were intentionally left  as comments in the event that further testing is required
 */
public class ACKRecieve extends Thread {

	private DatagramSocket udpSocket;
	private FastFtp parent;
	private boolean terminated = false;

	/**
	 * Constructor for ACKRecieve class
	 * @param u
	 *        The UDP DatagramSocket	 * 
	 * @param p
	 *        The the FastFtp parent thread
	 */
	public ACKRecieve(DatagramSocket u, FastFtp p) {
		udpSocket = u;
		parent = p;
	}

	public void run() {

		while (!(terminated)) {
			byte[] recData = new byte[Segment.MAX_SEGMENT_SIZE];
			DatagramPacket recAck = new DatagramPacket(recData, recData.length);
			try {
				udpSocket.receive(recAck);
				Segment tempSeg = new Segment(recAck);
				//System.out.println(tempSeg);
				parent.processACK(tempSeg);
			} catch (IOException e) {

				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			
		}
	}

	/**
	 * Method to toggle the terminate condition to true
	 */
	public void terminate() {
		terminated = true;
		//System.out.println("isteminated");
	}


}
