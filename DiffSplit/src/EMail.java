import java.util.ArrayList;


public class EMail {

	private String subject;
	private String from;
	private StringBuilder to;
	private ArrayList<String> body;
	
	EMail () {
		body = new ArrayList<String>();
		to = new StringBuilder();
	}

	public void appendBody(ArrayList<String> message) {
		assert(message == null);
		body.addAll(message);
	}
	public String getTo() {
		return to.toString();
	}
	public void setTo(String to) {
		this.to.delete(0, this.to.length());
		this.to.append(to);
	}
	public ArrayList<String> getBody() {
		return body;
	}
	public void setBody(ArrayList<String> body) {
		this.body = body;
	}
	public String getSubject() {
		return subject;
	}
	public String getFrom() {
		return from;
	}
	public void addTo(String email) {
		if(to.length() == 0)
			to.append(email);
		else
			to.append(", " + email);
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public void setFrom(String from) {
		this.from = from;
	}

	public void appendBody(String string) {
		body.add(string);
	}

}
