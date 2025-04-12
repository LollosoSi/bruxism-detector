package bruxism_grapher2;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class Event {
	long millis;
	String time;
	String type;
	String notes;
	double duration;

	public Event(long millis, String time, String type, String notes, double duration) {
		this.millis = millis;
		this.time = time;
		this.type = type;
		this.notes = notes;
		this.duration = duration;
	}
}

class FileEventReader {
	public static ArrayList<Event> readCSV(String fileName) {
		ArrayList<Event> events = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
			String line;
			boolean firstLine = true;
			while ((line = br.readLine()) != null) {
				if (firstLine) {
					firstLine = false;
					continue;
				}
				String[] parts = line.split(";");
				if (parts.length < 3)
					continue;

				long millis = Long.parseLong(parts[0]);
				String time = parts[1];
				String type = parts[2];
				String notes = parts.length > 3 ? parts[3] : "";
				double duration = 0;
				try {
					duration = parts.length > 4 ? Double.parseDouble(parts[4]) : 0;
				}catch(Exception e) {
					
				}

				events.add(new Event(millis, time, type, notes, duration));
			}
		} catch (IOException e) {
			System.err.println("Error reading file: " + fileName);
			e.printStackTrace();
		}
		return events;
	}
}