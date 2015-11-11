import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.Queue;


public class Utils {
	
	public static ArrayList<Segment> fileToSegments(String fileName){
		File f = new File(fileName);
		long fSize = f.length();
		int numSegs = (int) ((fSize/1000)+1);
		ArrayList<Segment> segments = new ArrayList<Segment>(numSegs);
		// read the file into a byte array
		FileInputStream inFile;
		
		
		byte [] buf = new byte[Segment.MAX_PAYLOAD_SIZE];
		int count = 0;
		int seqNum = 0;
		try {
			inFile = new FileInputStream(f);
			
			// read piece of the file into byte array/break the file into segments
			while ((count = inFile.read(buf)) > 0) {
				byte[] tempBuf = Arrays.copyOf(buf,count);
				Segment tempSeg = new Segment(seqNum++,tempBuf);
				segments.add(tempSeg);				
			}		
			
			inFile.close();
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return segments;		
	}

}
