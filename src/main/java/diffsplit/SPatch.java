package diffsplit;

import java.util.ArrayList;
import java.util.List;

public class SPatch {

	private List<String> message;
	private List<Diff> diffList;
	private String title;

	SPatch() {
		message = new ArrayList<String>();
		diffList = new ArrayList<Diff>();
	};

	void addDiff(Diff diff) {
		diffList.add(diff);
	}

	public void setMessage(List<String> message) {
		this.message.addAll(message);
	}

	public void setTitle(String string) {
		this.title = string;
	}

	public List<Diff> getDiffs() {
		return diffList;
	}

	public List<String> getMessage() {
		return message;
	}

	public String getTitle() {
		return title;
	}

}
