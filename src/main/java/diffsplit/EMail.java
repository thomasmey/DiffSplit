package diffsplit;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EMail {

	private String subject;
	private String from;
	private StringBuilder to;
	private List<String> body;

	EMail () {
		body = new ArrayList<>();
		to = new StringBuilder();
	}

	public void appendBody(List<String> message) {
		assert(message != null);
		body.addAll(message);
	}
	public String getTo() {
		return to.toString();
	}
	public void setTo(String to) {
		this.to.delete(0, this.to.length());
		this.to.append(to);
	}
	public List<String> getBody() {
		return body;
	}
	public void setBody(List<String> body) {
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

	//FIXME: move to EMailWriter or something.
	public static void writeMail(PrintWriter writer, EMail mail, String messageId, String replyToMessageId, Map<String,String> additionalHeaders) {

		writeEmailHeaderLine(writer, "From", mail.getFrom());
		writeEmailHeaderLine(writer, "Subject:", mail.getSubject());
		writeEmailHeaderLine(writer, "From:", DiffSplit.getConfig().getProperty("mailSOB"));
		writeEmailHeaderLine(writer, "To:", mail.getTo());
		writeEmailHeaderLine(writer, "Content-Type:", "text/plain; charset=\"UTF-8\"");
		writeEmailHeaderLine(writer, "Mime-Version:", "1.0");
		writeEmailHeaderLine(writer, "Content-Transfer-Encoding:", "8bit");
		writeEmailHeaderLine(writer, "X-Patch:", "Cocci");
		writeEmailHeaderLine(writer, "X-Mailer:", "DiffSplit");
		if(messageId == null)
			messageId = Utility.createMessageId(0, Constants.MESSAGE_ID_TOOL);
		writeEmailHeaderLine(writer, "Message-ID:", messageId);

		if(replyToMessageId != null) {
			writeEmailHeaderLine(writer, "References:", replyToMessageId);
			writeEmailHeaderLine(writer, "In-Reply-To:", replyToMessageId);
		}
		if(additionalHeaders != null) {
			additionalHeaders.forEach( (k,v) -> writeEmailHeaderLine(writer, k,v));			
		}

		// finish header section
		writer.println("");

		for(String line: mail.getBody()) {
			writeEmailBodyLine(writer, line);
		}
	}

	private static void writeEmailBodyLine(PrintWriter writer, String line) {
		writer.println(line);
	}

	private static void writeEmailHeaderLine(PrintWriter writer, String field, String line) {

		assert line != null: field;
		List<String> sl = new ArrayList<String>();
		List<String> lines = Utility.splitLineOn(78, line);
		sl.addAll(lines);

		for(int i = 0, n = sl.size(); i < n; i++) {
			String l = sl.get(i);
			if(i == 0)
				writer.println(field + ' ' + l);
			else
				writer.println(' ' + l);
		}
	}
}
