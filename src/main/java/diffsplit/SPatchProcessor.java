package diffsplit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class SPatchProcessor implements Callable<List<SPatch>>{

	private static final int TEXT_WIDTH = 78;

	private enum ParserMode {INIT, WARNING, PATCH_START, PATCH_CONTENT, END_OF_FILE };
	private ParserMode oldmode = ParserMode.INIT;
	private ParserMode newmode = ParserMode.INIT;

	private File patch;

	private Diff currentDiff;
	private List<String> diff = new ArrayList<String>();
	private SPatch currentSPatch;
	private Properties messages;

	public SPatchProcessor(File patch, Properties messages) {
		this.patch = patch;
		this.messages = messages;
	}

	public List<SPatch> call() {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(patch));

			String name = patch.getName().substring(0, patch.getName().lastIndexOf('.'));
			String title = "Use ARRAY_SIZE macro";
			String foundWith =
					"Found with: find -type f -name \"*.c\" -o -name \"*.h\" | xargs perl -p -i -e 's/\\bsizeof\\s*\\(\\s*(\\w+)\\s*\\)\\s*\\ /\\s*sizeof\\s*\\(\\s*\\1\\s*\\[\\s*0\\s*\\]\\s*\\) /ARRAY_SIZE(\\1)/g' and manual check/verification."
					;
			currentSPatch = new SPatch(name, title, foundWith);
			{
				String message = messages.getProperty(name);
				assert message != null : patch.getName();
				List<String> messages = Utility.splitLineOn(78, message);
				messages.addAll(Utility.splitLineOn(TEXT_WIDTH, foundWith));
				currentSPatch.setMessage(messages);
			}

			// read first
			String currentLine = reader.readLine();

			while(currentLine != null) {

				if(currentLine.startsWith("Please check ")) {
					newmode = ParserMode.WARNING;
				} else if(currentLine.startsWith("diff")) {
					newmode = ParserMode.PATCH_START;

					if(oldmode == ParserMode.PATCH_START)
						newmode = ParserMode.PATCH_CONTENT;
				}

				checkModeChange();

				switch (newmode) {
				case PATCH_START:
				case PATCH_CONTENT:
					diff.add(currentLine);
					break;
				}
				oldmode = newmode;

				// read next
				currentLine = reader.readLine();
			}


			// all SPatches were parsed, now process them
			newmode = ParserMode.END_OF_FILE;
			checkModeChange();

			return Arrays.asList(currentSPatch);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if(reader != null) {
				try {
					reader.close();
				} catch (IOException e) {}
			}
		}

		return null;
	}


	// check for group change
	private void checkModeChange() {

		if (newmode != oldmode) {
			if(oldmode == ParserMode.PATCH_START || oldmode == ParserMode.PATCH_CONTENT) {
				currentDiff.setDiffContent(diff);
				diff = new ArrayList<String>();
			}
			if(newmode == ParserMode.PATCH_START || newmode == ParserMode.PATCH_CONTENT) {
				currentDiff = new Diff();
				currentSPatch.addDiff(currentDiff);
			}
		}
	}
}
