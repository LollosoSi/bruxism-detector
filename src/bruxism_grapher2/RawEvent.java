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

class FileRawEventReader {
	public static ArrayList<RawEvent> readCSV(String fileName) {
		ArrayList<RawEvent> events = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
			String line;
			boolean firstLine = true;
			while ((line = br.readLine()) != null) {
				if (firstLine) {
					firstLine = false;
					continue;
				}
				String[] parts = line.split(";");
				if (parts.length < 2)
					continue;

				long millis = Long.parseLong(parts[0]);
				boolean value = Boolean.parseBoolean(parts[1]);
				
				//System.out.println("Reading event: " + millis + " : " + value);

				events.add(new RawEvent(millis, value));
			}
		} catch (IOException e) {
			System.err.println("Error reading file: " + fileName);
			e.printStackTrace();
		}
		return events;
	}
}
