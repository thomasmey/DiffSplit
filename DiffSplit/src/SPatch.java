import java.util.ArrayList;


public class SPatch {

	private ArrayList<String> message;
	private ArrayList<Diff> diffList;
	private String title;
	
	SPatch() {
		diffList = new ArrayList<Diff>();
	};
	
	void addDiff(Diff diff) {
		diffList.add(diff);
	}

	public void setMessage(ArrayList<String> message) {
		this.message = message;
		if(message.get(0).startsWith("Message example to submit a patch:")) {
			message.remove(0);
			while(message.get(0).isEmpty())
				message.remove(0);
		}
		setTitle(message.get(0));
		while(!message.get(0).isEmpty())
			message.remove(0);
		while(message.get(0).isEmpty())
			message.remove(0);
	}

	private void setTitle(String string) {
		int i = string.lastIndexOf('.');
		if(i>=0)
			this.title = string.substring(0, i);
		else
			this.title = string;
	}

	public ArrayList<Diff> getDiffs() {
		return diffList;
	}

	public ArrayList<String> getMessage() {
		return message;
	}

	public String getTitle() {
		return title;
	}

}
