package bruxism_grapher2;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class RawEvent {
	long millis;
	boolean value;
	int fvalue;

	public RawEvent(long millis, boolean value, int fvalue) {
		this.millis = millis;
		this.value = value;
		this.fvalue = fvalue;
	}
}

