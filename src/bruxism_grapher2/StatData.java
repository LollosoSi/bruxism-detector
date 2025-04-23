package bruxism_grapher2;

public class StatData implements Comparable<StatData> {
	String session_name;
	double duration, clenching_rate, beeps_per_event, alarm_percentage, average_time_between_clenching, average_clench_duration, active_time_percentage, total_clench_duration;
	int clench_count, alarm_count, beep_count, button_count, stop_after_beeps, not_stop_after_beeps;

	String mood = "";
	boolean workout = false, hydrated = false, stressed = false, caffeine = false, anxious = false, alcohol = false, bad_meal = false, medications, day_pain = false, life_event = false;
	
	public StatData(String session_name, double duration, double clenching_rate, int clench_count, int alarm_count, int beep_count, int button_count, int stop_after_beeps, int not_stop_after_beeps, double beeps_per_event, double alarm_percentage, double average_time_between_clenching, double average_clench_duration, String mood, boolean workout, boolean hydrated, boolean stressed, boolean caffeine, boolean anxious, boolean alcohol, boolean bad_meal, boolean medications, boolean day_pain, boolean life_event, double active_time_percentage, double total_clench_duration) {
		this.session_name = session_name;
		this.duration = duration;
		this.clenching_rate = clenching_rate;
		this.clench_count = clench_count;
		this.alarm_count = alarm_count;
		this.beep_count = beep_count;
		this.button_count = button_count;
		this.stop_after_beeps = stop_after_beeps;
		this.not_stop_after_beeps = not_stop_after_beeps;
		this.beeps_per_event = beeps_per_event;
		this.alarm_percentage = alarm_percentage;
		this.average_clench_duration = average_clench_duration;
		this.average_time_between_clenching = average_time_between_clenching;
		this.workout = workout;
		this.hydrated = hydrated;
		this.stressed = stressed;
		this.caffeine = caffeine;
		this.anxious = anxious;
		this.alcohol = alcohol;
		this.bad_meal = bad_meal;
		this.medications = medications;
		this.day_pain = day_pain;
		this.life_event = life_event;
		this.active_time_percentage = active_time_percentage;
		this.total_clench_duration = total_clench_duration;
	}
	
	static String produce_csv_header () {
		return "Date;Duration;Clenching Rate (per hour);Jaw Events;Alarm Triggers;Beep Count;Button Presses;Stopped after beep;Did not stop after beeps;Avg beeps per event;Alarm %;Average clenching event pause (minutes);Average clenching duration (seconds);Total clench time (seconds);Active time ‰(permille);Workout;Hydrated;Stressed;Caffeine;Anxious;Alcohol;Bad meal;Medications;Day pain;Life event";
	}
	String produce_csv_line () {
		return session_name + ";" + ((int)duration + ":" + (int) ((duration % 1) * 60)) + ";" + String.valueOf(((int)(clenching_rate*100.0))/100.0).replace(".",",") + ";" + clench_count + ";" + alarm_count + ";" + beep_count+ ";" + button_count+ ";" + stop_after_beeps + ";" + not_stop_after_beeps + ";" + beeps_per_event + ";" + alarm_percentage + ";" + String.valueOf(average_time_between_clenching).replace(".",",")  + ";" + String.valueOf(average_clench_duration).replace(".",",") +";"+ String.valueOf(total_clench_duration).replace(".",",")+ ";" + String.valueOf(active_time_percentage).replace(".",",")
				+ ";" + workout + ";" + hydrated + ";" + stressed + ";" + caffeine + ";" + anxious + ";" + alcohol + ";" + bad_meal + ";" + medications + ";" + day_pain + ";" + life_event;
	}

	@Override
    public int compareTo(StatData other) {
        return this.session_name.compareTo(other.session_name);
    }
	
}