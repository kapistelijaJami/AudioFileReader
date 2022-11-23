package audiofilereader;

import java.time.Duration;
import java.util.Locale;
import timer.DurationFormat;

public class FFmpegProgress {
	public static double getProgress(String text, Duration dur) {
		int idx = text.indexOf("time=");
		if (idx == -1) {
			return -1;
		}
		text = text.substring(idx + 5);
		text = text.substring(0, text.indexOf(" "));
		
		Duration current = DurationFormat.parseSimple(text);
		
		
		
		if (dur == null) return -1;
		
		double percentage = current.toMillis() * 100.0 / dur.toMillis();
		System.out.format(Locale.US, "Progress:  %.1f%% \t" + text + "\n", percentage);
		
		return percentage;
	}
}
