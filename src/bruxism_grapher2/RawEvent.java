package bruxism_grapher2;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class RawEvent {
	long millis;
	boolean value;

	public RawEvent(long millis, boolean value) {
		this.millis = millis;
		this.value = value;

	}
}

