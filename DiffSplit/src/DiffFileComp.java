import java.io.File;
import java.util.Comparator;
import java.util.StringTokenizer;


public class DiffFileComp implements Comparator<Diff> {

	@Override
	public int compare(Diff arg0, Diff arg1) {
		
		StringTokenizer st1 = new StringTokenizer(arg0.getNewFile(), File.separator);
		StringTokenizer st2 = new StringTokenizer(arg1.getNewFile(), File.separator);
		String t1, t2;
		int rc;
		
		while ( st1.hasMoreTokens()== true && st2.hasMoreTokens() == true) {
			t1 = st1.nextToken();
			t2 = st2.nextToken();
			
			rc = t1.compareTo(t2);
			if (rc != 0) {
				// are we at directory level or is this the file? 

				// dir vs. dir
				if(st1.hasMoreTokens() == true && st2.hasMoreTokens() == true) {
					return rc;
				}

				// file vs. file
				if(st1.hasMoreTokens() == false && st2.hasMoreTokens() == false) {
					return rc;
				}

				// dir vs. file
				if(st1.hasMoreTokens() == true && st2.hasMoreTokens() == false) {
					return -1;
				}

				// file vs. dir
				if(st1.hasMoreTokens() == false && st2.hasMoreTokens() == true) {
					return +1;
				}

			}
				
		}

		return 0;
	}

}
