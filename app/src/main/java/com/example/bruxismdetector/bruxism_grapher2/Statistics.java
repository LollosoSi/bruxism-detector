package com.example.bruxismdetector.bruxism_grapher2;

import java.util.ArrayList;

public class Statistics {

	static StatData calcStats(String session_name, ArrayList<Event> events) {

		StatData sd = new StatData();

		String mood = "Neutral";

		for(Event e : events) {

			if(e.type.equals("MOOD")) {
				mood = e.notes;
			}

			if (e.type.equals("INFO")) {
				sd.addInfo(e.notes);
			}
		}

		// Count event types
		int clenchCount = (int) events.stream().filter(e -> e.type.equals("Clenching") && e.notes.equals("STARTED"))
				.count();
		int alarmCount = (int) events.stream().filter(e -> e.type.equals("Alarm") && e.notes.equals("STARTED")).count();
		int beepCount = (int) events.stream().filter(e -> e.type.equals("Beep")).count();
		int buttonCount = (int) events.stream().filter(e -> e.type.equals("Button")).count();
		double sessionDuration = events.get(events.size() - 1).millis / 3600000.0;
		double clenchingRate, beeps_per_event;
		try {
			clenchingRate = clenchCount / sessionDuration;
		} catch (Exception e) {
			clenchingRate = 0;
		}
		try {
			beeps_per_event = beepCount / clenchCount;
		} catch (Exception e) {
			beeps_per_event = 0;
		}

		int alarm_percentage;
		try {
			alarm_percentage = (int) ((alarmCount / (double) clenchCount) * 100.0);
		} catch (Exception e) {
			alarm_percentage = 0;
		}

		double avg_clench_duration = 0, total_clench_duration = 0;
		int duration_samples = 0;

		int collected_pauses = 0;
		long last_clench_end = 0;
		double avg_clench_pauses = 0;
		double active_time_percentage = 0;
		for (int i = 0; i < events.size(); i++) {
			Event e = events.get(i);
			if (e.type.equals("Clenching")) {
				switch (e.notes) {
					case "STARTED":

						if (last_clench_end != 0) {
							avg_clench_pauses += (e.millis - last_clench_end);
							collected_pauses++;
							last_clench_end = 0;
						}

						break;

					case "STOPPED":
						last_clench_end = e.millis;

						// Apply correction to duration if not ending with the alarm
						double dd = (e.duration - ((events.get(i - 1).type.equals("Alarm") && events.get(i - 1).notes.equals("STOPPED")) ? 0 : 8));
						if(dd>0)
							total_clench_duration += dd;
						if(dd > 1) {
							avg_clench_duration += (int) dd;
							duration_samples++;
						}

						break;

				}
			}
		}


		if (collected_pauses > 0)
			avg_clench_pauses = avg_clench_pauses / (double) collected_pauses;
		else
			avg_clench_pauses = 0;

		if(total_clench_duration > 0) {
			active_time_percentage = (total_clench_duration / ((events.get(events.size() - 1).millis - events.get(0).millis)/1000.0)) * 1000.0;
			active_time_percentage = ((int) ((active_time_percentage) * 1000.0)) / 1000.0;
			total_clench_duration = ((int) ((total_clench_duration) * 100.0)) / 100.0;


		}

		if (duration_samples > 0)
			avg_clench_duration = avg_clench_duration / (double) duration_samples;
		else
			avg_clench_duration = 0;

		// Bring to minutes
		avg_clench_pauses = ((int) ((avg_clench_pauses / 60000) * 100.0)) / 100.0;

		avg_clench_duration = ((int) ((avg_clench_duration) * 100.0)) / 100.0;

		// Calculate stop-after-beeps count
		int stopAfterBeeps = 0;
		for (int i = 0; i < events.size(); i++) {
			Event e = events.get(i);
			if (e.type.equals("Beep")) {
				boolean foundAlarm = false;
				boolean foundBeep = false;
				for (int j = i + 1; j < events.size(); j++) {
					Event next = events.get(j);
					if (next.type.equals("Alarm") && next.millis - e.millis <= 10000) {
						foundAlarm = true;
						break;
					} else if (next.type.equals("Beep") && next.millis - e.millis <= 10000) {
						foundBeep = true;
						break;
					}
				}
				if (!foundAlarm && !foundBeep)
					stopAfterBeeps++;
			}
		}


		sd.addData("Date", session_name);
		sd.addData("Duration", String.valueOf(((int)sessionDuration + ":" + (int) ((sessionDuration % 1) * 60))));
		sd.addData("Total clench time (seconds)", String.valueOf(total_clench_duration));
		sd.addData("Active time (permille)", String.valueOf(active_time_percentage));
		sd.addData("Clenching Rate (per hour)", String.valueOf(((int)(clenchingRate*100.0))/100.0));
		sd.addData("Stopped after beep %", String.valueOf(((int)(((double)stopAfterBeeps/(double)clenchCount)*10000.0))/100.0));
		sd.addData("Avg beeps per event", String.valueOf(beeps_per_event));
		sd.addData("Average clenching duration (seconds)", String.valueOf(avg_clench_duration));
		sd.addData("Average clenching event pause (minutes)", String.valueOf(avg_clench_pauses));
		sd.addData("Jaw Events", String.valueOf(clenchCount));
		sd.addData("Beep Count", String.valueOf(beepCount));
		sd.addData("Alarm Triggers", String.valueOf(alarmCount));
		sd.addData("Stopped after beep", String.valueOf(stopAfterBeeps));
		sd.addData("Alarm %", String.valueOf(alarm_percentage));
		sd.addData("Button Presses", String.valueOf(buttonCount));

		sd.addData("Mood", String.valueOf(mood));

		return sd;
	}

}
