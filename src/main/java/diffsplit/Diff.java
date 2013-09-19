package diffsplit;

import java.io.File;
import java.util.List;
import java.util.StringTokenizer;

public class Diff {

	private List<String> diffContent;
	private String oldFile;
	private String newFile;
	private List<Maintainer> maintainers;

	public List<String> getDiffContent() {
		return diffContent;
	}

	public void setDiffContent(List<String> diffContent) {
		this.diffContent = diffContent;

		parseCommandLine(diffContent.get(0));
	}

	private void parseCommandLine(String string) {
		StringTokenizer st = new StringTokenizer(string);
		String ct;
		while(st.hasMoreElements()) {
			ct = st.nextToken();
			if(ct.startsWith("diff"))
				continue;
			if(ct.startsWith("-"))
				continue;
			if(oldFile==null) {
				setOldFile(ct);
				continue;
			}
			if(newFile==null) {
				setNewFile(ct);
				continue;
			}
		}
	}

	public String getOldFile() {
		return oldFile;
	}

	public void setOldFile(String oldFile) {
		this.oldFile = oldFile.substring(oldFile.indexOf(File.separatorChar) + 1);
	}

	public String getNewFile() {
		return newFile;
	}

	public void setNewFile(String newFile) {
		this.newFile = newFile.substring(newFile.indexOf(File.separatorChar) + 1);
	}
	List<Maintainer> getMaintainers() {
		return maintainers;
	}

	public void setMaintainers(List<Maintainer> maintainers) {
		assert maintainers != null;
		this.maintainers = maintainers;
	}
}
