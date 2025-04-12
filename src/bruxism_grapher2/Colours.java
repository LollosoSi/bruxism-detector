package bruxism_grapher2;

import java.awt.Color;

public class Colours {

	static enum Color_element {
		Background, Text, Button, Clenching, Alarm, Warning, Clenchfill;
	};

	static Color light_mode[] = {
			Color.WHITE,
			Color.DARK_GRAY,
			Color.ORANGE,
			Color.RED,
			Color.CYAN,
			Color.MAGENTA,
			Color.BLUE
	};

	static Color dark_mode[] = {
			Color.DARK_GRAY,
			Color.WHITE,
			Color.ORANGE,
			Color.RED,
			Color.CYAN,
			Color.MAGENTA,
			Color.BLUE
	};

	static Color getColor(Color_element element, boolean use_dark_mode) {
		return (use_dark_mode ? dark_mode : light_mode)[element.ordinal()];
	}

}
