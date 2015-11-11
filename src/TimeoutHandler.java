import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;


public class TimeoutHandler extends TimerTask {

	private Timer timer;
	private FastFtp parent;
	
	public TimeoutHandler(Timer t, FastFtp p){
		timer = t;
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
