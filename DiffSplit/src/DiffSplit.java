import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Properties;

public class DiffSplit {

	private int oldmode = 0, newmode = 0;
	private String currentLine = null;
	private SPatch currentSPatch = null;
	private Diff   currentDiff = null;
	private ArrayList<SPatch> spatchList = new ArrayList<SPatch>();
	private ArrayList<String> message = new ArrayList<String>();
	private ArrayList<String> diff = new ArrayList<String>();
	private PrintWriter mboxWriter;
	private BufferedReader inputReader;
	private int patchNumber = 1;
	private Properties props;
	private File linuxDir;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		String propFileName = "diffsplit.properties";
		new DiffSplit(args, propFileName);
	}
	
	DiffSplit(String[] args, String propFileName) {
		
		props = new Properties();
		try {
			props.load(new FileReader(propFileName));
		} catch (FileNotFoundException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		} catch (IOException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
			return;
		}

		linuxDir = new File(props.getProperty("linuxDir"));
		Reader reader;
		try {
			reader = new BufferedReader(new FileReader(args[0]));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
		inputReader = new BufferedReader(reader);

		String mboxFileName = props.getProperty("mboxFileName");
		try {
			mboxWriter = new PrintWriter(new BufferedWriter(new FileWriter(mboxFileName)));
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return;
		}

		processFile();
		try {
			reader.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		mboxWriter.close();
		
	}
	
	private void processFile() {

		// read first
		try {
			currentLine = inputReader.readLine();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		while(currentLine!=null) {

			
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
				message.add(currentLine);
				break;
			case 3:
			case 4:
				diff.add(currentLine);
				break;
			}
			oldmode = newmode;

			// read next
			try {
				currentLine = inputReader.readLine();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		newmode = 9;
		checkModeChange();

		EMail currentMail;
		for(SPatch sp: spatchList) {
			
			Comparator<Diff> sortComp = new DiffFileComp();
			
			Collections.sort(sp.getDiffs(), sortComp);

			currentMail = createNewMail(sp); 

			Diff prevDi = null;
			for(Diff di: sp.getDiffs()) {

				if(checkExcludePath(di.getNewFile()))
					continue;
				if(checkExcludeGitCommitDate(di.getNewFile()))
					continue;

				// check for same path!
				if(prevDi!=null) {
					String path1 = prevDi.getNewFile().substring(0, prevDi.getNewFile().lastIndexOf(File.separatorChar));
					String path2 = di.getNewFile().substring(0, di.getNewFile().lastIndexOf(File.separatorChar));
					if (path1.compareTo(path2) == 0) {
						// put this changes in one email
						currentMail.appendBody(di.getDiffContent());
					} else {
						currentMail.setSubject("[PATCH] " + getGittLogPrefix(prevDi.getNewFile()) + ": "+ sp.getTitle());
						prevDi.setMaintainers(getMaintainer(prevDi.getNewFile()));
						appendMailTo(currentMail,prevDi.getMaintainers());
						try {
							writeMail(currentMail);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
							return;
						}
						// new email
						currentMail = createNewMail(sp);
						currentMail.appendBody(di.getDiffContent());
					}
				} else {
					currentMail.appendBody(di.getDiffContent());
				}
				prevDi = di;
			}
		}
	}

	private boolean checkExcludeGitCommitDate(String newFile) {

		Process proc;
		try {
			proc = Runtime.getRuntime().exec("git log -n 1 --no-merges --pretty=format:%ct -- " + newFile + "\n", null, linuxDir);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}

		BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

		try {
			proc.waitFor();
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		if(proc.exitValue() != 0) {
			InputStream err = proc.getErrorStream();
			int b = 0;
			try {
				b = err.read();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			while(b >= 0) {
				System.out.append((char)b);
				try {
					b=err.read();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			return false;
		}
		
		String currentLine = null;
		try {
			currentLine = reader.readLine();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

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

	private String getGittLogPrefix(String newFile) {
		
		HashMap<String,Integer> hs = new HashMap<String, Integer>();
		
		Process proc;
		try {
			proc = Runtime.getRuntime().exec("git log -n 10 --no-merges --pretty=format:%s -- " + newFile + "\n", null, linuxDir);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}

		BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

		try {
			proc.waitFor();
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		if(proc.exitValue() != 0) {
			InputStream err = proc.getErrorStream();
			int b = 0;
			try {
				b = err.read();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			while(b >= 0) {
				System.out.append((char)b);
				try {
					b=err.read();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			return null;
		}
		
		String currentLine = null;
		try {
			currentLine = reader.readLine();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		String prefix;
		int iDoppel,iStrich,iEnd;
		Integer iCounter;

		while(currentLine!= null) {
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
			try {
				currentLine = reader.readLine();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		Iterator<Entry<String, Integer>> i1 =hs.entrySet().iterator();
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
			ArrayList<Maintainer> maintainers) {

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

	private void writeMail(EMail mail) throws IOException {
		

		mboxWriter.println("From " + mail.getFrom());
		mboxWriter.println("Subject: " + mail.getSubject());
		mboxWriter.println("From: " + mail.getFrom());
		mboxWriter.println("To: " + mail.getTo());
		mboxWriter.println("Content-Type: text/plain; charset=\"UTF-8\"");
		mboxWriter.println("Mime-Version: 1.0");
		mboxWriter.println("Content-Transfer-Encoding: 8bit");
		mboxWriter.println("");
		for(String line: mail.getBody()) {
			mboxWriter.println(line);
		}
		patchNumber++;
	}

	private ArrayList<Maintainer> getMaintainer(String newFile) {

		Process proc;
		try {
			proc = Runtime.getRuntime().exec("./scripts/get_maintainer.pl -f " + newFile, null, linuxDir);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}

		BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

		try {
			proc.waitFor();
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		if(proc.exitValue() != 0)
			return null;
		
		String currentLine = null;
		try {
			currentLine = reader.readLine();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		ArrayList<Maintainer> maArray = new ArrayList<Maintainer>();
		while(currentLine!= null) {

			// parse output and create output table
			Maintainer ma = parseMaintainer(currentLine);
			maArray.add(ma);
			try {
				currentLine = reader.readLine();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		return maArray;
	}

	private Maintainer parseMaintainer(String mainLine) {
		
		if(mainLine == null)
			return null;
		if(mainLine.isEmpty())
			return null;

		int i = mainLine.indexOf('<');
		Maintainer m = new Maintainer();
		
		// no name provided!
		if(i<0) {
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



