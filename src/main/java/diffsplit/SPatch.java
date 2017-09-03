package diffsplit;

import java.util.ArrayList;
import java.util.List;

public class SPatch {

	private List<String> message;
	private List<Diff> diffList;
	private final String name;
	private final String title;
	private final String foundWith;

	SPatch(String sname, String title, String foundWith) {
		if(sname == null) throw new IllegalArgumentException();

		message = new ArrayList<String>();
		diffList = new ArrayList<Diff>();
		this.name = sname;

		if(title == null) {
			this.title = "Cocci spatch \"" + getName() + "\"";
		} else {
			this.title = title;
		}

		if(foundWith == null) {
			this.foundWith = "Found by coccinelle spatch \"" + Utility.findPath(getName()) +"\"";
		} else {
			this.foundWith = foundWith;
		}
	};

	void addDiff(Diff diff) {
		diffList.add(diff);
	}

	public void setMessage(List<String> message) {
		this.message.addAll(message);
	}

	public List<Diff> getDiffs() {
		return diffList;
	}

	public List<String> getMessage() {
		return message;
	}

	public String getTitle() {
		return this.title;
	}

	public String getName() {
		return this.name;
	}

	public String getFoundWith() {
		return this.foundWith;
	}

}
