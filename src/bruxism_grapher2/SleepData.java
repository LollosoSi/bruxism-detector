package bruxism_grapher2;

import java.util.ArrayList;

public class SleepData {

	final static int REM = 4, LIGHT_SLEEP = 2, DEEP_SLEEP = 3, AWAKE = 1;
	
	public class SleepStage {
		public SleepStage(long u_s, long u_s_end, int val) {
			unix_sec = u_s;
			unix_sec_end = u_s_end;
			value = val;
		}

		public long unix_sec;
		public long unix_sec_end;
		public int value;
	};

	public class Record {
		public Record(long u_s, int val) {
			unix_sec = u_s;
			value = val;
		}

		public long unix_sec;
		public int value;
	};

	public int duration_deepsleep = -1, duration_lightsleep = -1, duration_rem = -1, duration_sleep = -1, awake_count = -1, duration_awake = -1, average_hr = -1, average_breath_quality = -1;

	public void setSleepStats(int duration_deepsleep, int duration_lightsleep, int duration_rem, int duration_sleep, int awake_count, int duration_awake, int average_hr, int average_breath_quality) {
		this.duration_deepsleep = duration_deepsleep;
		this.duration_lightsleep = duration_lightsleep;
		this.duration_rem = duration_rem;
		this.duration_sleep = duration_sleep;
		this.awake_count = awake_count;
		this.duration_awake = duration_awake;
		this.average_hr = average_hr;
		this.average_breath_quality = average_breath_quality;
	}
	
	public ArrayList<SleepStage> sleep_stages = new ArrayList<>();
	public ArrayList<Record> heartrate = new ArrayList<>();
	public ArrayList<Record> spo2 = new ArrayList<>();
	public ArrayList<Record> stress = new ArrayList<>();

	public SleepData() {}

	public void addSleepStage(String[] data) {
		try {
			if (data.length == 3) {
				sleep_stages.add(
						new SleepStage(Long.parseLong(data[0]), Long.parseLong(data[1]), Integer.parseInt(data[2])));
			} else {
				System.out.println("Sleep data dropped as it is incomplete: length " + data.length);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void addHeartrate(String[] data) {
		try {
			if (data.length == 2) {
				heartrate.add(
						new Record(Long.parseLong(data[0]), Integer.parseInt(data[1])));
			} else {
				System.out.println("Heartrate data dropped as it is incomplete: length " + data.length);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void addStress(String[] data) {
		try {
			if (data.length == 2) {
				stress.add(
						new Record(Long.parseLong(data[0]), Integer.parseInt(data[1])));
			} else {
				System.out.println("Stress data dropped as it is incomplete: length " + data.length);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void addSpo2(String[] data) {
		try {
			if (data.length == 2) {
				spo2.add(
						new Record(Long.parseLong(data[0]), Integer.parseInt(data[1])));
			} else {
				System.out.println("SpO2 data dropped as it is incomplete: length " + data.length);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
