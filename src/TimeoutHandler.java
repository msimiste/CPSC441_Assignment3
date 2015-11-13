import java.io.IOException;
import java.util.TimerTask;

/**
 * 
 * @author Mike Simister
 * November 11, 2015
 * 
 * This class handles a timeout, ie a timer task for the
 * Timer. The Timer belongs to the parent thread FastFtp.
 * Should a timeout occur, this thead calls a method in the
 * parent thread in order to process the timeout.
 *
 */
public class TimeoutHandler extends TimerTask {

	private FastFtp parent;
	
	/**
	 * Constructor method
	 * @param p
	 *        The FastFtp parent thread.
	 */
	public TimeoutHandler(FastFtp p){
		parent = p;
		
	}
	@Override
	public void run() {
		try {
			parent.processTimeout();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
