package com.example.bruxismdetector.bruxism_grapher2;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class Event {
	public long millis;
	public String time;
	public String type;
	public String notes;
	public double duration;

	public Event(long millis, String time, String type, String notes, double duration) {
		this.millis = millis;
		this.time = time;
		this.type = type;
		this.notes = notes;
		this.duration = duration;
	}
}

