package com.example.bruxismdetector.bruxism_grapher2;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;

public class FileSleepReader {

	public FileSleepReader() {

	}

	public static SleepData readCSV(String sleepdatafile) {
		SleepData sd = new SleepData();
		try {
			readSleepStages(sd, sleepdatafile);
		} catch (Exception e) {
		}
		try {
			readHr(sd, sleepdatafile.replace("sleepdata", "hr"));
		} catch (Exception e) {
		}
		try {
			readStress(sd, sleepdatafile.replace("sleepdata", "stress"));
		} catch (Exception e) {
		}
		try {
			readSpo2(sd, sleepdatafile.replace("sleepdata", "spo2"));
		} catch (Exception e) {
		}

		return sd;
	}

	public static void readSleepStages(SleepData sd, String fileName) {
		try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
			String line;
			boolean firstLine = true;
			boolean secondLine = true;
			boolean thirdLine = true;
			while ((line = br.readLine()) != null) {
				if (firstLine) {
					firstLine = false;
					continue;
				}
				if (secondLine) {
					int[] values = new int[8];
					Arrays.fill(values, 0);
					int i = 0;
					for (String s : line.split(";")) {
						values[i++] = (int) Float.parseFloat(s);
					}
					sd.setSleepStats(values[0], values[1], values[2], values[3], values[4], values[5], values[6],
							values[7]);
					secondLine = false;
					continue;
				}
				if (thirdLine) {
					thirdLine = false;
					continue;
				}

				String[] parts = line.split(";");
				sd.addSleepStage(parts);

			}
		} catch (IOException e) {
			System.err.println("Error reading file: " + fileName);
			//e.printStackTrace();
		}
	}

	public static void readHr(SleepData sd, String fileName) {
		try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
			String line;
			while ((line = br.readLine()) != null) {

				String[] parts = line.split(";");
				sd.addHeartrate(parts);

			}
		} catch (IOException e) {
			System.err.println("Error reading file: " + fileName);
			//e.printStackTrace();
		}
	}

	public static void readStress(SleepData sd, String fileName) {
		try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
			String line;
			while ((line = br.readLine()) != null) {

				String[] parts = line.split(";");
				sd.addStress(parts);

			}
		} catch (IOException e) {
			System.err.println("Error reading file: " + fileName);
			//e.printStackTrace();
		}
	}

	public static void readSpo2(SleepData sd, String fileName) {
		try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
			String line;
			while ((line = br.readLine()) != null) {

				String[] parts = line.split(";");
				sd.addSpo2(parts);

			}
		} catch (IOException e) {
			System.err.println("Error reading file: " + fileName);
			//e.printStackTrace();
		}
	}

}
