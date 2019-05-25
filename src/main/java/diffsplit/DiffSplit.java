package diffsplit;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
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

import javax.json.Json;
import javax.json.JsonObject;

public class DiffSplit implements Runnable {

	private static Properties props;

	private Logger log = Logger.getLogger(DiffSplit.class.getName());

	private List<SPatch> spatchList = new ArrayList<>();
	private File linuxDir;
	private File patchFile;
	private SimpleDateFormat fromDateFormater;
	private JsonObject messages;
	private String runId;

	private int fileCounter;

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
			throw new IllegalArgumentException("Must provided run/commit id!");

		if(args.length < 2)
			throw new IllegalArgumentException("Must provided patch file or directory!");

		runId = args[0];

		// get commit messages for the spatches
		InputStream inputStream = this.getClass().getResourceAsStream("messages.json");
		this.messages = Json.createReader(new InputStreamReader(inputStream)).readObject();

		linuxDir = new File(props.getProperty(Constants.LINUX_DIR));
		patchFile = new File(args[1]);
		fromDateFormater = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", Locale.US);
	}

	public void run() {

		File[] patches = getPatches();
		patchFile.listFiles();
		for(File patch : patches) {
			SPatchProcessor processor = null;
			processor = new SPatchProcessor(patch, this.messages);
			List<SPatch> spatches = processor.call();
			spatchList.addAll(spatches);
		}

		// Parse SPatch objects
		for(SPatch sp: spatchList) {
			Comparator<Diff> sortComp = new DiffFileComp();

			Collections.sort(sp.getDiffs(), sortComp);
			List<EMail> mails = new ArrayList<EMail>();

			try {
				{
					Diff prevDi = null;
					EMail currentMail = createNewMail(sp); 
					for(Diff di: sp.getDiffs()) {
						log.log(Level.INFO, "Processing spatch \"{0}\" - diff \"{1}\"", new String[] { sp.getName(), di.getOldFile() } );

						if(checkExcludePath(di.getNewFile())) {
							log.log(Level.INFO, "excluded by path");
							continue;
						}

						if(!checkIncludePath(di.getNewFile())) {
							log.log(Level.INFO, "not included by path");
							continue;
						}

						if(checkExcludeGitCommitDate(di.getNewFile())) {
							log.log(Level.INFO, "excluded by date");
							continue;
						}

						// check for same path!
						if(prevDi != null) {
							String path1 = prevDi.getNewFile().substring(0, prevDi.getNewFile().lastIndexOf(File.separatorChar));
							String path2 = di.getNewFile().substring(0, di.getNewFile().lastIndexOf(File.separatorChar));
							if (path1.compareTo(path2) == 0 || isOnTryHarderList(path1, path2)) {
								// put this changes in one email
								currentMail.appendBody(di.getDiffContent());
							} else {
								currentMail.setSubject(getGitLogPrefix(prevDi.getNewFile()) + ": "+ sp.getTitle());
								prevDi.setMaintainers(Utility.getMaintainer(prevDi.getNewFile()));
								appendMailTo(currentMail, prevDi.getMaintainers());
								mails.add(currentMail);

								// new email
								currentMail = createNewMail(sp);
								currentMail.appendBody(di.getDiffContent());
							}
						} else {
							// first diff in spatch
							currentMail.appendBody(di.getDiffContent());
						}
						prevDi = di;
					}
					//FIXME: is this correct? or only in case of one diff in one spatch?
					currentMail.setSubject(getGitLogPrefix(prevDi.getNewFile()) + ": "+ sp.getTitle());
					prevDi.setMaintainers(Utility.getMaintainer(prevDi.getNewFile()));
					appendMailTo(currentMail, prevDi.getMaintainers());
					mails.add(currentMail);
				}

				//run patch content though checkpatch program
				if(props.getProperty("doCheckpatch", Boolean.TRUE.toString()).equals(Boolean.TRUE.toString())) {
					for(int i = 0; i < mails.size();) {
						EMail mail = mails.get(i);
						Process process = Runtime.getRuntime().exec(new String[] {"scripts/checkpatch.pl", "-"}, null, linuxDir);
						{
							BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
							for(String str : mail.getBody()) {
								writer.write(str);
								writer.newLine();
							}
							writer.close();
						}
						int rc = process.waitFor();
						if(rc != 0) {
							List<String> result = new ArrayList<String>();
							BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
							String currentLine = reader.readLine();
							while(currentLine != null) {
								result.add(currentLine);
								currentLine = reader.readLine();
							}
							reader.close();
							PrintWriter writer = new PrintWriter(sp.getName() + '.' + i + ".checkpatch.rej");
							mail.appendBody("Checkpatch output:");
							mail.appendBody(result);
							EMail.writeMail(writer, mail, null, null, null);
							writer.close();
							mails.remove(i);
						} else {
							i++;
						}
					}
				}

				log.info("Created " + mails.size() + " emails");
				// create cover email
				if(mails.size() > 0) {
					EMail cover = new EMail();
					String currentDate = fromDateFormater.format(new Date());
					cover.setFrom(props.getProperty("mailFrom") + ' ' + currentDate);
					String subject = "Cocci spatch \"" + sp.getName() + "\"" + " - " + runId;
					cover.setSubject(subject);

					String message = messages.getJsonObject(sp.getName()).getString("body");
					List<String> messages = Utility.splitLineOn(78, message);
					messages.add("");
					messages.addAll(Utility.splitLineOn(78, sp.getFoundWith()));
					messages.add("");
					messages.add("Run against version " + runId);
					messages.add("");
					messages.add("P.S. If you find this email unwanted, set up a procmail rule junking on"); 
					messages.add("the header:"); 
					messages.add("");
					messages.add("X-Patch: Cocci");
					cover.appendBody(messages);
					cover.setTo("linux-kernel@vger.kernel.org");
					mails.add(0, cover);

					// write emails to mbox file
					String coverMessageId = null;
					for(int i = 0, n = mails.size() - 1; i <= n; i++) {
						Map<String, String> addHeaders = new HashMap<>();

						EMail mail = mails.get(i);
						subject = mail.getSubject();
						if(i > 0) { // not cover email
							subject = "[PATCH] " + subject;
//							subject = "[PATCH " + i + "/" + n +"] " + subject;
							addHeaders.put("X-Serial-No:", String.valueOf(i));
						}
						mail.setSubject(subject);
						String messageId = Utility.createMessageId(i, Constants.MESSAGE_ID_TOOL);
						String baseName = props.getProperty("mboxFileName");
						try(PrintWriter mboxWriter = new PrintWriter(baseName + '.' + fileCounter++, StandardCharsets.UTF_8.name())) {
							EMail.writeMail(mboxWriter, mail, messageId, coverMessageId, addHeaders);
						}
						if(i == 0) {
							coverMessageId = messageId;
						}
					}
				}

			} catch (InterruptedException | IOException e) {
				e.printStackTrace();
			}
		}
	}

	private File[] getPatches() {
		if(patchFile.isDirectory()) {
			return patchFile.listFiles(f -> {
				if(f.isFile() && f.getName().endsWith(".spatch"))
					return true;
				else return false;
			});
		} else if(patchFile.isFile()) {
			return new File[] {patchFile};
		} else {
			return new File[0];
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

		int rc = proc.waitFor();
		if(rc != 0) {
			BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
			String line = reader.readLine();
			while(line != null) {
				log.log(Level.SEVERE, line);
				line = reader.readLine();
			}
			return false;
		}

		BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
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

	private static boolean checkPath(Properties props, String searchKey, String path) {
		Enumeration<Object> e1 = props.keys();
		while(e1.hasMoreElements()) {
			Object key = e1.nextElement();
			if(key instanceof String) {
				if(((String)key).equals(searchKey)) {
					Object value = props.get(key);

					if(value instanceof String) {
					    if(value.equals("**"))
					       return true;

						String[] paths = ((String) value).split(",");
						for(String p: paths) {
							if (path.startsWith(p))
								return true;
						}
					}
				}
			}
		}
		return false;
	}

	private boolean checkExcludePath(String path) {
		return checkPath(props, "excludePath", path);
	}

	private boolean checkIncludePath(String path) {
		return checkPath(props, "includePath", path);
	}

	private String getGitLogPrefix(String newFile) throws IOException, InterruptedException {

		Map<String,Integer> hs = new HashMap<String, Integer>();

		Process proc = Runtime.getRuntime().exec("git log -n 10 --no-merges --pretty=format:%s -- " + newFile + "\n", null, linuxDir);

		int rc = proc.waitFor();
		if(rc != 0) {
			BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
			String line = reader.readLine();
			while(line != null) {
				log.log(Level.SEVERE, line);
				line = reader.readLine();
			}
			return null;
		}

		BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
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
			   m.getRole().startsWith("supporter", i) ||
			   m.getRole().startsWith("moderated list", i) ||
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

	public static Properties getConfig() {
		return DiffSplit.props;
	}
}
