package com.example.bruxismdetector.bruxism_grapher2;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class RawEvent {
	public long millis;
	public boolean value;
	public int fvalue;

	public RawEvent(long millis, boolean value, int fvalue) {
		this.millis = millis;
		this.value = value;
		this.fvalue = fvalue;
	}
}

