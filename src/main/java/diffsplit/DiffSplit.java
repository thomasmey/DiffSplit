package diffsplit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

public class DiffSplit implements Runnable {

	private int oldmode, newmode;
	private SPatch currentSPatch;
	private Diff currentDiff;
	private List<SPatch> spatchList = new ArrayList<SPatch>();
	private List<String> message = new ArrayList<String>();
	private List<String> diff = new ArrayList<String>();
	private int patchNumber = 1;
	private Properties props;
	private File linuxDir;
	private File patchDir;
	private PrintWriter mboxWriter;

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		new DiffSplit(args).run();
	}

	DiffSplit(String[] args) throws IOException {

		InputStream configInputStream = this.getClass().getResourceAsStream("config.xml");
		props = new Properties();
		props.loadFromXML(configInputStream);

		linuxDir = new File(props.getProperty("linuxDir"));
		patchDir = new File(args[0]);
	}

	public void run() {

		File[] patches = patchDir.listFiles();
		for(File patch : patches) {
			if(!patch.getName().endsWith(".spatch"))
				continue;

			BufferedReader reader = null;
			try {
				reader = new BufferedReader(new FileReader(patch));
				mboxWriter = new PrintWriter(props.getProperty("mboxFileName"), "UTF-8");

				// every file just contains one spatch
				currentSPatch = new SPatch();
				spatchList.add(currentSPatch);
				currentSPatch.setTitle("Cocci spatch \"" + patch.getName() + "\"");

				// read first
				String currentLine = reader.readLine();

				int fixSpaceIndex;

				while(currentLine != null) {

					if(currentLine.startsWith("Processing ")) {
						newmode = 1;
					}
					if(currentLine.startsWith("Message example to submit a patch:")) {
						newmode = 2;
					}
					if(currentLine.startsWith("diff")) {
						newmode = 3;

						if(oldmode == 3)
							newmode = 4;
					}

					checkModeChange();

					switch (newmode) {
					case 2:
						for(fixSpaceIndex=0; fixSpaceIndex < currentLine.length() && currentLine.charAt(fixSpaceIndex) == ' '; fixSpaceIndex++);

						if(fixSpaceIndex>0 && fixSpaceIndex < currentLine.length())
							currentLine = currentLine.substring(fixSpaceIndex);

						message.add(currentLine);
						break;
					case 3:
					case 4:
						diff.add(currentLine);
						break;
					}
					oldmode = newmode;

					// read next
					currentLine = reader.readLine();
				}
			} catch (IOException e) {
				e.printStackTrace();
				return;
			} finally {
				if(reader != null) {
					try {
						reader.close();
					} catch (IOException e) {}
				}
			}
		}

		// all SPatches were parsed, now process them
		newmode = 9;
		checkModeChange();

		EMail currentMail;
		for(SPatch sp: spatchList) {

			Comparator<Diff> sortComp = new DiffFileComp();

			Collections.sort(sp.getDiffs(), sortComp);

			currentMail = createNewMail(sp); 

			try {
			Diff prevDi = null;
			for(Diff di: sp.getDiffs()) {

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
						prevDi.setMaintainers(getMaintainer(prevDi.getNewFile()));
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

		Date minCommitDate = null;
		if(currentLine!= null) {
			minCommitDate = new Date(Long.parseLong(currentLine) * 1000);
		}

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
		currentMail.setFrom(props.getProperty("mailFrom"));
		currentMail.appendBody(sp.getMessage());
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
		patchNumber++;
	}

	private void writeEmailBodyLine(String line) {
		mboxWriter.println(line);
	}

	private void writeEmailHeaderLine(String field, String line) {

		List<String> sl = new ArrayList<String>();
		int maxPos = 78;
		while(line.length() > maxPos) {
			int iSpace = line.lastIndexOf(' ', maxPos);
			if(iSpace >= 0) {
				String ss = line.substring(0, iSpace);
				sl.add(ss);
				line = line.substring(iSpace + 1);
			}
		}
		sl.add(line);

		for(int i = 0, n = sl.size(); i < n; i++) {
			String l = sl.get(i);
			if(i == 0)
				mboxWriter.println(field + ' ' + l);
			else
				mboxWriter.println(' ' + l);
		}
	}

	private List<Maintainer> getMaintainer(String newFile) throws IOException, InterruptedException {

		Process proc = Runtime.getRuntime().exec("./scripts/get_maintainer.pl -f " + newFile, null, linuxDir);

		BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

		proc.waitFor();

		if(proc.exitValue() != 0)
			return null;

		String currentLine = reader.readLine();

		ArrayList<Maintainer> maArray = new ArrayList<Maintainer>();
		while(currentLine!= null) {

			// parse output and create output table
			Maintainer ma = parseMaintainer(currentLine);
			maArray.add(ma);
			currentLine = reader.readLine();
		}

		return maArray;
	}

	private static Maintainer parseMaintainer(String mainLine) {

		if(mainLine == null)
			return null;
		if(mainLine.isEmpty())
			return null;

		int i = mainLine.indexOf('<');
		Maintainer m = new Maintainer();

		// no name provided!
		if(i < 0) {
			i = mainLine.indexOf(' ');
			m.setEmail(mainLine.substring(0, i));
			m.setRole(mainLine.substring(i+1, mainLine.length()));
		} else {
			m.setName(mainLine.substring(0, i-1));
			int i2 = mainLine.indexOf('>');
			if(i2 < 0) {
				// somethings wrong with the output of the pearl script!
				// try to find the next space
				i2 = mainLine.indexOf(' ', i);
			}
			m.setEmail(mainLine.substring(i+1, i2));
			m.setRole(mainLine.substring(i2 + 2, mainLine.length()));

		}
		return m;
	}

	// check for group change
	private void checkModeChange() {

		if (newmode != oldmode) {
			if (newmode == 1) {
				currentSPatch = new SPatch();
				spatchList.add(currentSPatch);
			}
			if(newmode == 2) {
				message = new ArrayList<String>();
			}
			if(oldmode == 2) {
				currentSPatch.setMessage(message);
			}
			if(oldmode == 3 || oldmode == 4) {
				currentDiff.setDiffContent(diff);
				diff = new ArrayList<String>();
			}
			if(newmode == 3 || newmode == 4) {
				currentDiff = new Diff();
				currentSPatch.addDiff(currentDiff);
			}
		}
	}
}
