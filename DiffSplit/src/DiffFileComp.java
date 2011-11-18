import java.util.Comparator;


public class DiffFileComp implements Comparator<Diff> {

	@Override
	public int compare(Diff arg0, Diff arg1) {
		
		return arg0.getNewFile().compareTo(arg1.getNewFile());
	}

}
