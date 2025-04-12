package bruxism_grapher2;

import java.util.ArrayList;

public class Statistics {

	static StatData calcStats(String session_name, ArrayList<Event> events) {

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

		double avg_clench_duration = 0;
		int duration_samples = 0;

		int collected_pauses = 0;
		long last_clench_end = 0;
		double avg_clench_pauses = 0;
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

		if (duration_samples > 0)
			avg_clench_duration = avg_clench_duration / (double) duration_samples;
		else
			avg_clench_duration = 0;

		// Bring to minutes
		avg_clench_pauses = ((int) ((avg_clench_pauses / 60000) * 100.0)) / 100.0;
		
		avg_clench_duration = ((int) ((avg_clench_duration) * 100.0)) / 100.0;

		// Calculate stop-after-beeps count
		int stopAfterBeeps = 0;
		int notStopAfterBeeps = 0;
		for (int i = 0; i < events.size(); i++) {
			Event e = events.get(i);
			if (e.type.equals("Beep")) {
				boolean foundAlarm = false;
				for (int j = i + 1; j < events.size(); j++) {
					Event next = events.get(j);
					if (next.type.equals("Alarm") && next.millis - e.millis <= 10000) {
						foundAlarm = true;
						notStopAfterBeeps++;
						break;
					} else if (next.type.equals("Beep")) {
						break;
					}
				}
				if (!foundAlarm)
					stopAfterBeeps++;
			}
		}

		StatData sd = new StatData(session_name, sessionDuration, clenchingRate, clenchCount, alarmCount, beepCount,
				buttonCount, stopAfterBeeps, notStopAfterBeeps, beeps_per_event, alarm_percentage, avg_clench_pauses,
				avg_clench_duration);
		return sd;
	}

}
