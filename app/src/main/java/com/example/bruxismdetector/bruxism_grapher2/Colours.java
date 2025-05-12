package com.example.bruxismdetector.bruxism_grapher2;
public class Colours {

	static enum Color_element {
		Background, Text, Button, Clenching, Alarm, Warning, Clenchfill, Clenchline, Clenchline_guide;
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
			"#808080"  // GRAY
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
			"#808080"  // GRAY
	};

	static String getColor(Color_element element, boolean use_dark_mode) {
		return (use_dark_mode ? dark_mode : light_mode)[element.ordinal()];
	}

}
