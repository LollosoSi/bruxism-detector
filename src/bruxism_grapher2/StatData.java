package bruxism_grapher2;

public class StatData {
	String session_name;
	double duration, clenching_rate, beeps_per_event, alarm_percentage, average_time_between_clenching, average_clench_duration;
	int clench_count, alarm_count, beep_count, button_count, stop_after_beeps, not_stop_after_beeps;

	public StatData(String session_name, double duration, double clenching_rate, int clench_count, int alarm_count, int beep_count, int button_count, int stop_after_beeps, int not_stop_after_beeps, double beeps_per_event, double alarm_percentage, double average_time_between_clenching, double average_clench_duration) {
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
	}
	
	static String produce_csv_header () {
		return "Date;Duration;Clenching Rate (per hour);Jaw Events;Alarm Triggers;Beep Count;Button Presses;Stopped after beep; Did not stop after beeps; Avg beeps per event; Alarm %;Average clenching event pause (minutes);Average clenching duration (seconds)";
	}
	String produce_csv_line () {
		return session_name + ";" + ((int)duration + ":" + (int) ((duration % 1) * 60)) + ";" + String.valueOf(((int)(clenching_rate*100.0))/100.0).replace(".",",") + ";" + clench_count + ";" + alarm_count + ";" + beep_count+ ";" + button_count+ ";" + stop_after_beeps + ";" + not_stop_after_beeps + ";" + beeps_per_event + ";" + alarm_percentage + ";" + String.valueOf(average_time_between_clenching).replace(".",",")  + ";" + String.valueOf(average_clench_duration).replace(".",",") ;
	}

	public int compareTo(StatData b) {
		// Sort by date
		return this.session_name.compareTo(b.session_name);
	}

}