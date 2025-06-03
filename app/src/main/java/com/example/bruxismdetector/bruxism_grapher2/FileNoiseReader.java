package com.example.bruxismdetector.bruxism_grapher2;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class FileNoiseReader {
	public static ArrayList<NoiseEvent> readCSV(String fileName) {
		ArrayList<NoiseEvent> events = new ArrayList<>();
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
				double db = Double.parseDouble(parts[1]);
				
				//System.out.println("Reading event: " + millis + " : " + value);

				events.add(new NoiseEvent(millis, db));
			}
		} catch (IOException e) {
			System.err.println("Error reading file: " + fileName);
			e.printStackTrace();
		}
		return events;
	}
}
