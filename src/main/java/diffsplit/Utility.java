package diffsplit;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class Utility {

	public static String findPath(String sname) {
		String dir = DiffSplit.getConfig().getProperty(Constants.LINUX_DIR) + "/scripts/coccinelle/";
		try {
			Process process = Runtime.getRuntime().exec(new String[] {"find", dir, "-name", sname + "*"} );
			process.waitFor();

			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String currentLine =  reader.readLine();
			reader.close();
			return currentLine.substring(dir.length());
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return null;
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


	public static List<Maintainer> getMaintainer(String newFile) throws IOException, InterruptedException {

		Properties props = DiffSplit.getConfig();
		File linuxDir = new File(props.getProperty(Constants.LINUX_DIR));

		Process proc = Runtime.getRuntime().exec("./scripts/get_maintainer.pl -f " + newFile, null, linuxDir);

		BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

		proc.waitFor();

		if(proc.exitValue() != 0)
			return Collections.emptyList();

		ArrayList<Maintainer> maArray = new ArrayList<Maintainer>();
		String currentLine = reader.readLine();
		while(currentLine != null) {

			// parse output and create output table
			Maintainer ma = Utility.parseMaintainer(currentLine);
			maArray.add(ma);
			currentLine = reader.readLine();
		}

		return maArray;
	}

	public static List<String> splitLineOn(int maxPos, String line) {
		List<String> sl = new ArrayList<String>();
		while(line.length() > maxPos) {
			int iSpace = line.lastIndexOf(' ', maxPos);
			if(iSpace >= 0) {
				String ss = line.substring(0, iSpace);
				sl.add(ss);
				line = line.substring(iSpace + 1);
			}
		}
		sl.add(line);
		return sl;
	}
}
