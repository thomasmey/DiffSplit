package diffsplit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DiffSplit implements Runnable {

	private List<SPatch> spatchList = new ArrayList<SPatch>();
	private static Properties props;
	private File linuxDir;
	private File patchDir;
	private PrintWriter mboxWriter;
	private SimpleDateFormat fromDateFormater;
	private Properties messages;
	private Logger log;

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {

		// get global config option
		InputStream inputStream = DiffSplit.class.getResourceAsStream("config.xml");
		DiffSplit.props = new Properties();
		DiffSplit.props.loadFromXML(inputStream);

		new DiffSplit(args).run();
	}

	DiffSplit(String[] args) throws IOException {

		if(args.length < 1)
			throw new IllegalArgumentException("Must provided spatch directory!");

		// get commit messages for the spatches
		InputStream inputStream = this.getClass().getResourceAsStream("messages.xml");
		this.messages = new Properties();
		this.messages.loadFromXML(inputStream);

		linuxDir = new File(props.getProperty(Constants.LINUX_DIR));
		patchDir = new File(args[0]);
		fromDateFormater = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", Locale.US);
		log = Logger.getAnonymousLogger();
	}

	public void run() {

		if(!patchDir.isDirectory())
			throw new IllegalArgumentException("patchDir must be a directory!");

		// Parse all spatch files in the given directory
		File[] patches = patchDir.listFiles();
		for(File patch : patches) {
			if(!patch.isFile())
				continue;

			if(!patch.getName().endsWith(".spatch"))
				continue;

			SPatchProcessor processor = null;
			processor = new SPatchProcessor(patch, this.messages);
			List<SPatch> spatches = processor.call();
			spatchList.addAll(spatches);
		}

		// setup output writer
		try {
			mboxWriter = new PrintWriter(props.getProperty("mboxFileName"), "UTF-8");
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
			return;
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
			return;
		}

		// Parse SPatch objects
		EMail currentMail;
		for(SPatch sp: spatchList) {
			Comparator<Diff> sortComp = new DiffFileComp();

			Collections.sort(sp.getDiffs(), sortComp);

			currentMail = createNewMail(sp); 

			try {
			Diff prevDi = null;
			for(Diff di: sp.getDiffs()) {
				log.log(Level.INFO, "Processing spatch \"{0}\" - diff \"{1}\"", new String[] { sp.getName(), di.getOldFile() } );

				if(checkExcludePath(di.getNewFile()))
					continue;
				if(checkExcludeGitCommitDate(di.getNewFile()))
					continue;

				// check for same path!
				if(prevDi != null) {
					String path1 = prevDi.getNewFile().substring(0, prevDi.getNewFile().lastIndexOf(File.separatorChar));
					String path2 = di.getNewFile().substring(0, di.getNewFile().lastIndexOf(File.separatorChar));
					if (path1.compareTo(path2) == 0 || isOnTryHarderList(path1, path2)) {
						// put this changes in one email
						currentMail.appendBody(di.getDiffContent());
					} else {
						currentMail.setSubject("[PATCH] " + getGittLogPrefix(prevDi.getNewFile()) + ": "+ sp.getTitle());
						prevDi.setMaintainers(Utility.getMaintainer(prevDi.getNewFile()));
						appendMailTo(currentMail,prevDi.getMaintainers());
						writeMail(currentMail);

						// new email
						currentMail = createNewMail(sp);
						currentMail.appendBody(di.getDiffContent());
					}
				} else {
					currentMail.appendBody(di.getDiffContent());
				}
				prevDi = di;
			}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private boolean isOnTryHarderList(String path1, String path2) {

		Enumeration<Object> e1 = props.keys();
		while(e1.hasMoreElements()) {
			Object key = e1.nextElement();
			if(key instanceof String) {
				if(((String)key).startsWith("mergeHarder")) {
					Object value = props.get(key);

					if(value instanceof String) {
						if (path1.startsWith((String)value) && path2.startsWith((String)value))
							return true;
					}
				}
			}
		}

		return false;
	}

	/**
	 * 
	 * @param newFile the file name in the linux directory to get the git log. 
	 * @return true, if this file should be excluded from processing, false if file should be included in processing.
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private boolean checkExcludeGitCommitDate(String newFile) throws IOException, InterruptedException {

		Process proc;
		try {
			proc = Runtime.getRuntime().exec("git log -n 1 --no-merges --pretty=format:%ct -- " + newFile + "\n", null, linuxDir);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

		proc.waitFor();

		if(proc.exitValue() != 0) {
			InputStream err = proc.getErrorStream();
			int b = 0;
			b = err.read();
			while(b >= 0) {
				System.out.append((char)b);
				b=err.read();
			}
			return false;
		}

		String currentLine = reader.readLine();
		if(currentLine == null)
			return false;

		Date minCommitDate = new Date(Long.parseLong(currentLine) * 1000);

		Calendar cal1 = Calendar.getInstance();
		cal1.setTime(minCommitDate);

		Calendar cal2 = Calendar.getInstance();
		String months = props.getProperty("excludeGitCommitDateAgeMonth");
		int iMonths = Integer.valueOf(months) * -1;
		cal2.add(Calendar.MONTH, iMonths);

		if(cal1.before(cal2))
			return true;

		return false;
	}

	private boolean checkExcludePath(String newFile) {
		Enumeration<Object> e1 = props.keys();
		while(e1.hasMoreElements()) {
			Object key = e1.nextElement();
			if(key instanceof String) {
				if(((String)key).startsWith("excludePath")) {
					Object value = props.get(key);

					if(value instanceof String) {
						if (newFile.startsWith((String)value))
							return true;
					}
				}
			}
		}

		return false;
	}

	private String getGittLogPrefix(String newFile) throws IOException, InterruptedException {

		Map<String,Integer> hs = new HashMap<String, Integer>();

		Process proc = Runtime.getRuntime().exec("git log -n 10 --no-merges --pretty=format:%s -- " + newFile + "\n", null, linuxDir);

		BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

		proc.waitFor();

		if(proc.exitValue() != 0) {
			InputStream err = proc.getErrorStream();
			int b = 0;
			b = err.read();
			while(b >= 0) {
				System.out.append((char)b);
				b=err.read();
			}
			return null;
		}

		String currentLine = reader.readLine();

		String prefix;
		int iDoppel,iStrich,iEnd;
		Integer iCounter;

		while(currentLine != null) {
			// parse output and check most used prefix
			prefix = null;

			if(currentLine.startsWith("Merge branch") ||
					currentLine.startsWith("Revert"))
				;
			else {
				iEnd = 0;
				iStrich = currentLine.lastIndexOf(" - ");
				iDoppel = currentLine.lastIndexOf(':');
				if(iStrich >= 0 && iStrich > iDoppel )
					iEnd = --iStrich;
				else
					if(iDoppel >= 0)
						iEnd = iDoppel;

				if(iEnd > 0)
					prefix=currentLine.substring(0, iEnd);
			}

			if(prefix!= null) {
				iCounter = hs.get(prefix);
				if(iCounter == null)
					iCounter = new Integer(1);
				else
					iCounter++;
				hs.put(prefix, iCounter);
			}

			currentLine = reader.readLine();
		}

		Iterator<Entry<String, Integer>> i1 = hs.entrySet().iterator();
		Entry<String,Integer> resultSet;
		Integer maxValue = 0;
		String  maxKey = null;
		while(i1.hasNext()) {
			resultSet = i1.next();
			if(resultSet.getValue() >= maxValue) {
				maxValue=resultSet.getValue();
				maxKey = resultSet.getKey();
			}
		}
		return maxKey != null ? maxKey : "?";
	}

	private void appendMailTo(EMail currentMail,
			List<Maintainer> maintainers) {

		int i = 0;
		for(Maintainer m: maintainers) {

			if(m.getRole().charAt(i)== '(')
				i++;
			if(m.getRole().startsWith("maintainer", i) ||
					m.getRole().startsWith("open list", i) ) {
				currentMail.addTo(m.getEmail());
			}
		}

	}

	private EMail createNewMail(SPatch sp) {

		EMail currentMail = new EMail();
		String currentDate = fromDateFormater.format(new Date());
		currentMail.setFrom(props.getProperty("mailFrom") + ' ' + currentDate);
		currentMail.appendBody(sp.getMessage());
		currentMail.appendBody("");
		currentMail.appendBody("Signed-off-by: " + props.getProperty("mailSOB"));
		currentMail.appendBody("---");
		currentMail.appendBody("");
		return currentMail;
	}

	private void writeMail(EMail mail) {

		writeEmailHeaderLine("From", mail.getFrom());
		writeEmailHeaderLine("Subject:", mail.getSubject());
		writeEmailHeaderLine("From:", mail.getFrom());
		writeEmailHeaderLine("To:", mail.getTo());
		writeEmailHeaderLine("Content-Type:", "text/plain; charset=\"UTF-8\"");
		writeEmailHeaderLine("Mime-Version:", "1.0");
		writeEmailHeaderLine("Content-Transfer-Encoding:", "8bit");

		// finish header section
		mboxWriter.println("");

		for(String line: mail.getBody()) {
			writeEmailBodyLine(line);
		}
//		patchNumber++;
	}

	private void writeEmailBodyLine(String line) {
		mboxWriter.println(line);
	}

	private void writeEmailHeaderLine(String field, String line) {

		List<String> sl = new ArrayList<String>();
		List<String> lines = Utility.splitLineOn(78, line);
		sl.addAll(lines);

		for(int i = 0, n = sl.size(); i < n; i++) {
			String l = sl.get(i);
			if(i == 0)
				mboxWriter.println(field + ' ' + l);
			else
				mboxWriter.println(' ' + l);
		}
	}

	public static Properties getConfig() {
		return DiffSplit.props;
	}
}
