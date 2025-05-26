package bruxism_grapher2;

public class Colours {

	static enum Color_element {
		Background, Text, Button, Clenching, Alarm, Warning, Clenchfill, Clenchline, Clenchline_guide, Hrline, Spoline, Stressline, ResetBlock, Spoline_warning, Spoline_danger;
	};

	static String[] light_mode = {
			"#ffffff", // WHITE
			"#404040", // DARK_GRAY
			"#ffa500", // ORANGE
			"#ff0000", // RED
			"#00ffff", // CYAN
			"#ff00ff", // MAGENTA
			"#0000ff", // BLUE
			"#00ff00", // GREEN
			"#808080",  // GRAY
			"#F44336", // RED
			"#40C4FF", // BLUE
			"#00E676", // GREEN
			"#37474F", // Reset is gray black (or white)
			"#F9A825", // Spoline warning (material orange)
			"#FF4081"  // Spoline danger (material pink)
	};

	static String[] dark_mode = {
			"#404040", // DARK_GRAY
			"#ffffff", // WHITE
			"#ffa500", // ORANGE
			"#ff0000", // RED
			"#00ffff", // CYAN
			"#ff00ff", // MAGENTA
			"#0000ff", // BLUE
			"#00ff00", // GREEN
			"#808080",  // GRAY
			"#F44336", // RED
			"#40C4FF", // BLUE
			"#00E676", // GREEN
			"#BDBDBD", // Reset is gray black (or gray white)
			"#F9A825", // Spoline warning (material orange)
			"#FF4081" // Spoline danger (material pink)
	};

	static String getColor(Color_element element, boolean use_dark_mode) {
		return (use_dark_mode ? dark_mode : light_mode)[element.ordinal()];
	}

}
