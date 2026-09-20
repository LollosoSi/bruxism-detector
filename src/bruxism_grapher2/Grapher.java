package bruxism_grapher2;


import java.lang.reflect.Array;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Arrays;
import java.util.Calendar;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import bruxism_grapher2.RawEvent;
import bruxism_grapher2.Colours.Color_element;
import bruxism_grapher2.grapher_interfaces.*;


public class Grapher<Image, Color, Font> {

	public void setPlatformSpecificAbstractions(GrapherInterface<Color, Image, Font> g, IconManager<Color, Image> im, TaskRunner tr) {
		gi = g;
		icm = im;
		taskRunner = tr;
		calculateGraphParameters();
	}

	public boolean only_info = false; // Do not draw graphs if this is true.. no data in file
	StatData sd = null;
	SleepData sleepData = null;

	public SleepData getSleepData() {
		return sleepData;
	}

	public void setSleepData(SleepData sleepData) {
		this.sleepData = sleepData;
	}

	int graph_width, graph_height;
	long min_time, max_time;
	double time_scale, xhour;

	int side_margin, side_info_margin;

	int timeline_height, legend_height, first_slot_height, info_text_height;
	int slot_spacing, slot_height;

	int tick_length, tick_slot_length, xcharsize;

	long sync_unix_second_time_start = 0;

	int clenchline_height_low, clenchline_height_high;
	int heartrate_height_low, heartrate_height_high;
	int spo2_height_low, spo2_height_high;
	int stress_height_low, stress_height_high;

	int noise_height_low, noise_height_high;

	static final int
			alarm_slot = 0, alarm_slot_length = 3,
			clenching_slot = 4, clenching_slot_length = 1,
			button_slot = 1, button_slot_length = 2,
			beep_slot = 0, beep_slot_length = 1;

	String file_name;

	// =========================================================
	// SOGLIE STATISTICHE UNIFICATE
	// =========================================================
	public double THRESHOLD_CORRELATION_MIN = 0.15;  // Valore minimo di Pearson per essere considerato "rilevante"
	public double THRESHOLD_P_VALUE_MAX = 0.05;      // Valore massimo per l'affidabilità statistica
	public double THRESHOLD_EFFECT_SIZE_MIN = 4.0;   // Valore minimo di E% per l'impatto clinico

	public int max_minute_correlation_delay = 90;

	// ====
	ArrayList<Event> events;
	ArrayList<RawEvent> raw_events = null;
	ArrayList<NoiseEvent> noise_events = null;

	ArrayList<NoiseEvent> accel_mag_events = null;


	GrapherInterface<Color, Image, Font> gi = null;
	IconManager<Color, Image> icm = null;
	TaskRunner taskRunner = null;
	Map<String, IconAndNiceness> icons = new HashMap<String, IconAndNiceness>();

	public void addNoiseData(ArrayList<NoiseEvent> noises) {
		noise_events = noises;
	}

	public void addAccelData(ArrayList<NoiseEvent> acceldata) {
		accel_mag_events = acceldata;
	}

	public class IconAndNiceness{
		public IconAndNiceness(Image ic, String nice) {
			icon = ic;
			niceness = nice;
		}
		Image icon;
		String niceness = Neutral;
	};

	public Grapher(ArrayList<Event> event_list, String file_name, int width, int height) {
		events = event_list;
		this.file_name = file_name;

		graph_width = width;
		graph_height = height;

		if(events.get(0).type.equals("ONLY_INFO") || events.get(1).type.equals("ONLY_INFO")){
			only_info=true;
		}
	}

	void calculateGraphParameters() {

		tick_length = 20;
		tick_slot_length = 20;

		side_margin = graph_width / 12;
		side_info_margin = graph_width / 20;
		info_text_height = 50;

		legend_height = graph_height - 55;

		int line_height_temp = legend_height;

		if(raw_events != null) {
			if(!raw_events.isEmpty()) {
				clenchline_height_low = (line_height_temp -= 20);
				clenchline_height_high = (line_height_temp -= 25);
			}
		}

		if(!sleepData.heartrate.isEmpty() || !sleepData.stress.isEmpty()) {
			heartrate_height_low = (line_height_temp -= 20);
			heartrate_height_high = (line_height_temp -= 25);
		}

		if(!sleepData.spo2.isEmpty() ) {
			spo2_height_low = (line_height_temp -= 20);
			spo2_height_high = (line_height_temp -= 25);
		}

		//if(!sleepData.stress.isEmpty()) {
		//	stress_height_low = (line_height_temp -= 25);
		//	stress_height_high = (line_height_temp -= 30);
		//}

		if(noise_events != null || accel_mag_events != null) {
			noise_height_low = (line_height_temp -= 20);
			noise_height_high = (line_height_temp -= 25);
		}

		timeline_height = (line_height_temp -= 80);

		first_slot_height = timeline_height;
		slot_height = 20;
		slot_spacing = 5;

		min_time = events.get(0).millis;
		max_time = events.get(events.size() - 1).millis;
		time_scale = (graph_width - 2 * side_margin) / (double) (max_time - min_time);
		xhour = time_scale * (6000 * 60);
		xcharsize = 9;

		loadIcons();

	}

	static final String
			Bad = "#F44336",        // Material Red 500
			Mediocre = "#FF9800",   // Material Orange 500
			Neutral = "#2196F3",    // Material Blue 500
			Nice = "#4CAF50";       // Material Green 500

	public void loadIcons() {


		icons.put("android", new IconAndNiceness(icm.loadImage("android.png", Nice), Nice));
		icons.put("medication", new IconAndNiceness(icm.loadImage("medication.png", Bad), Bad));
		icons.put("stressed", new IconAndNiceness(icm.loadImage("stressed.png", Bad), Bad));
		icons.put("alcohol", new IconAndNiceness(icm.loadImage("alcohol.png", Mediocre), Mediocre));
		icons.put("skipped or late dinner", new IconAndNiceness(icm.loadImage("bad_meal.png", Mediocre), Mediocre));
		icons.put("pain", new IconAndNiceness(icm.loadImage("day_pain.png", Bad), Bad));
		icons.put("workout", new IconAndNiceness(icm.loadImage("workout.png", Nice), Nice));
		icons.put("hydrated", new IconAndNiceness(icm.loadImage("hydrated.png", Neutral), Neutral));
		icons.put("caffeine", new IconAndNiceness(icm.loadImage("coffee.png", Mediocre), Mediocre));
		icons.put("life event", new IconAndNiceness(icm.loadImage("life_event.png", Mediocre), Mediocre));
		icons.put("anxious", new IconAndNiceness(icm.loadImage("anxiety.png", Mediocre), Mediocre));
		icons.put("sick", new IconAndNiceness(icm.loadImage("sick.png", Mediocre), Mediocre));
		icons.put("bad", new IconAndNiceness(icm.loadImage("bad.png", Bad), Bad));
		icons.put("good", new IconAndNiceness(icm.loadImage("good.png", Nice), Nice));
		icons.put("botox", new IconAndNiceness(icm.loadImage("botox.png", Nice), Nice));
		icons.put("onlyalarm", new IconAndNiceness(icm.loadImage("onlyalarms.png", Neutral), Neutral));
		icons.put("tired", new IconAndNiceness(icm.loadImage("tired.png", Mediocre), Mediocre));
		icons.put("mouth guard", new IconAndNiceness(icm.loadImage("mouthguard.png", Neutral), Neutral));
		icons.put("treatment: mouth guard", new IconAndNiceness(icm.loadImage("mouthguard.png", Neutral), Neutral));


		icons.put("donotbeep", new IconAndNiceness(icm.loadImage("no_beep.png", Neutral), Neutral));
		icons.put("donotalarm", new IconAndNiceness(icm.loadImage("no_alarm.png", Neutral), Neutral));

	}


	long findmsfromchars(int chars) {
		return (long) (((chars * 9)) / time_scale);
	}

	int xtimescale(long millis) {
		return side_margin + (int) (time_scale * millis);
	}

	void drawEventLine(long millis, String time, int slot_start, int slot_end, boolean text_right,
					   Color cline, Color ctext) {
		// drawTimeTick(millis, time, slot_start, text_right, cline, ctext);
		gi.setColor(cline);
		gi.drawLine(xtimescale(millis), getBaseYslot(slot_start), xtimescale(millis), getBaseYslot(slot_end));

	}

	void drawTimeTick(long millis, String time, int slot, boolean text_right, Color cline, Color ctext) {
		gi.setColor(cline);
		gi.drawLine(xtimescale(millis), timeline_height, xtimescale(millis),
				timeline_height + tick_length + (tick_slot_length * slot));
		gi.setColor(ctext);
		gi.drawString(time, xtimescale(millis) - (text_right ? -5 : (xcharsize * time.length())),
				timeline_height + tick_length + (tick_slot_length * slot));
	}

	void drawTimeBaseTick(long millis_start, String time_start, long millis_end) {
		int hour = Integer.valueOf(time_start.split(":")[0]);
		int minutes_to_hour = 60 - Integer.valueOf(time_start.split(":")[1]);
		int minutes_to_half = 30 - Integer.valueOf(time_start.split(":")[1]);

		long cur_millis = minutes_to_hour == 0 || minutes_to_hour == 60 ? millis_start
				: millis_start + (60000 * minutes_to_hour);
		long millis_half = minutes_to_half > 0 ? millis_start + (60000 * minutes_to_half) : cur_millis + (60000 * 30);
		do {
			hour++;
			gi.drawLine(xtimescale(cur_millis), timeline_height, xtimescale(cur_millis),
					(int) (timeline_height + tick_length + (tick_slot_length * 1)));
			String time = String.format(" %02d:00", hour % 24);
			gi.drawString(time, xtimescale(cur_millis) - (xcharsize * time.length()) / 2,
					timeline_height + tick_length + (tick_slot_length * 2));

			cur_millis += (60000 * 60);
		} while (cur_millis < millis_end);

		do {

			gi.drawLine(xtimescale(millis_half), timeline_height, xtimescale(millis_half),
					(int) (timeline_height + tick_length + (tick_slot_length * 0.2)));

			millis_half += (60000 * 60);
		} while (millis_half < millis_end);
	}

	int getBaseYslot(int slot) {
		return first_slot_height - (slot * (slot_spacing + slot_height));
	}

	void drawDurationRectangle(long millis_start, long millis_stop, int slot, String text_top,
							   Color cslot, Color ctext, Color fillcolor, int text_slot) {
		gi.setColor(fillcolor);
		gi.fillRect(xtimescale(millis_start), getBaseYslot(slot), xtimescale(millis_stop) - xtimescale(millis_start),
				slot_height);
		gi.setColor(cslot);
		gi.drawRect(xtimescale(millis_start), getBaseYslot(slot), xtimescale(millis_stop) - xtimescale(millis_start),
				slot_height);
		gi.setColor(ctext);
		gi.drawString(text_top, xtimescale(millis_stop) - (xcharsize * text_top.length()),
				getBaseYslot(slot) - (16 * text_slot));
	}
public class Sample_Correlation {
		long time;
		double value;
		public Sample_Correlation(long time, double value){
			this.time = time;
			this.value = value;
		}

};
ArrayList<Sample_Correlation> samples_clench = new ArrayList<Sample_Correlation>();
ArrayList<Sample_Correlation> samples_hr = new ArrayList<Sample_Correlation>();
ArrayList<Sample_Correlation> samples_spo2 = new ArrayList<Sample_Correlation>();
ArrayList<Sample_Correlation> samples_stress = new ArrayList<Sample_Correlation>();
ArrayList<Sample_Correlation> samples_sleepstages = new ArrayList<Sample_Correlation>();
ArrayList<Sample_Correlation> samples_noise = new ArrayList<Sample_Correlation>();
ArrayList<Sample_Correlation> samples_accel = new ArrayList<Sample_Correlation>();


ArrayList<Correlations.CorrelationPair> delayed_corrs_hr = null;
	ArrayList<Correlations.CorrelationPair> delayed_raw_corrs_hr = null;
ArrayList<Correlations.CorrelationPair> delayed_corrs_spo2 = null;
	ArrayList<Correlations.CorrelationPair> delayed_raw_corrs_spo2 = null;
ArrayList<Correlations.CorrelationPair> delayed_corrs_accel = null;


	int findIndexFromTime(long mintime, long maxtime, long time, int numsamples){
		if(time < mintime) time = mintime;
		if(time > maxtime) time = maxtime;
		if(maxtime <= mintime) return 0; // Previene divisioni per zero

		int samples = (int)Math.ceil((double) (maxtime - mintime) /1000.0);
		long step = Math.max(1, (maxtime - mintime)/samples); // Sicurezza

		return (int) ((time-mintime)/step);
	}
	double[] createSampledArray(ArrayList<Sample_Correlation> samples, int numsamples){
		double[] result = new double[numsamples];
		Arrays.fill(result, 0);

		if (samples.isEmpty()) return result;

		// 1. Riempiamo lo spazio PRIMA del primo campione (Head)
		int firstEnd = findIndexFromTime(min_time, max_time, samples.get(0).time, numsamples);
		firstEnd = Math.max(0, Math.min(firstEnd, numsamples));
		for(int j = 0; j < firstEnd; j++){
			result[j] = samples.get(0).value;
		}

		// 2. Riempiamo il corpo centrale
		for(int i = 0; i<samples.size()-1; i++){
			int startfill = findIndexFromTime(min_time, max_time, samples.get(i).time, numsamples);
			int endfill = findIndexFromTime(min_time, max_time, samples.get(i+1).time, numsamples);

			startfill = Math.max(0, Math.min(startfill, numsamples));
			endfill = Math.max(0, Math.min(endfill, numsamples));

			for(int j = startfill; j<endfill; j++){
				result[j] = samples.get(i).value;
			}
		}

		// 3. Riempiamo lo spazio DOPO l'ultimo campione (Tail)
		int finalStart = findIndexFromTime(min_time, max_time, samples.get(samples.size()-1).time, numsamples);
		finalStart = Math.max(0, Math.min(finalStart, numsamples));
		for (int j = finalStart; j < numsamples; j++) {
			result[j] = samples.get(samples.size()-1).value;
		}

		return result;
	}

	double[] createLinearSampledArray(ArrayList<Sample_Correlation> samples, int numsamples) {
		double[] result = new double[numsamples];
		Arrays.fill(result, 0);

		if (samples.isEmpty()) return result;

		// 1. Spazio PRIMA del primo campione (mantiene piatto il primo valore)
		int firstEnd = findIndexFromTime(min_time, max_time, samples.get(0).time, numsamples);
		firstEnd = Math.max(0, Math.min(firstEnd, numsamples));
		for(int j = 0; j < firstEnd; j++){
			result[j] = samples.get(0).value;
		}

		// 2. Corpo centrale: INTERPOLAZIONE LINEARE
		for(int i = 0; i < samples.size() - 1; i++){
			int startfill = findIndexFromTime(min_time, max_time, samples.get(i).time, numsamples);
			int endfill = findIndexFromTime(min_time, max_time, samples.get(i+1).time, numsamples);

			startfill = Math.max(0, Math.min(startfill, numsamples));
			endfill = Math.max(0, Math.min(endfill, numsamples));

			double valStart = samples.get(i).value;
			double valEnd = samples.get(i+1).value;
			int span = endfill - startfill;

			for(int j = startfill; j < endfill; j++){
				if (span <= 0) break;
				// Calcola il punto esatto sulla linea tra valStart e valEnd
				double fraction = (double)(j - startfill) / span;
				result[j] = valStart + fraction * (valEnd - valStart);
			}
		}

		// 3. Spazio DOPO l'ultimo campione (mantiene piatto l'ultimo valore)
		int finalStart = findIndexFromTime(min_time, max_time, samples.get(samples.size()-1).time, numsamples);
		finalStart = Math.max(0, Math.min(finalStart, numsamples));
		for (int j = finalStart; j < numsamples; j++) {
			result[j] = samples.get(samples.size()-1).value;
		}

		return result;
	}

	public double findValueForTime(ArrayList<Sample_Correlation> samples, long time){
		Sample_Correlation psc = samples.get(0);
		for(Sample_Correlation sc : samples){
			if(time <= sc.time && time >= psc.time){
				return psc.value;
			}
		}
		return -1;
	}

	void drawSamplesLineDebug(double[] samples, String name, double floor, double scale){
		int height_low =  getBaseYslot(0)-((int) ((slot_height+slot_spacing) * floor));
		gi.setColor(gi.convertColor(Colours.getColor(Color_element.Text, true)));
		gi.drawString(name, xtimescale(min_time) - 9 * 4, height_low + 7);
		ArrayList<Color> colors = new ArrayList<Color>();
		colors.add(gi.convertColor(Colours.getColor(Color_element.Spoline, true)));
		colors.add(gi.convertColor(Colours.getColor(Color_element.Clenchline, true)));
		colors.add(gi.convertColor(Colours.getColor(Color_element.Alarm, true)));
		colors.add(gi.convertColor(Colours.getColor(Color_element.Text, true)));
		colors.add(gi.convertColor(Colours.getColor(Color_element.Stressline, true)));

		for(int i = 1; i < samples.length; i++) {
			if(samples[i]<0 || samples[i]>=colors.size()){
				gi.setColor(colors.get(0));
			}else {
				gi.setColor(colors.get((int) samples[i]));
			}

			gi.drawLine(xtimescale(min_time+((i-1)*1000L)), height_low-(int)((slot_spacing/scale)*samples[i-1]), xtimescale((long) min_time+(i*1000L)), height_low-(int)((slot_spacing/scale)*samples[i]));
		}


	}
	void drawSamplesArraysLineDebug(ArrayList<Sample_Correlation> samples, String name, double floor, double scale){
		int height_low =  getBaseYslot(0)-((int) ((slot_height+slot_spacing) * floor));
		gi.setColor(gi.convertColor(Colours.getColor(Color_element.Text, true)));
		gi.drawString(name, xtimescale(min_time) - 9 * 4, height_low + 7);
		ArrayList<Color> colors = new ArrayList<Color>();
		colors.add(gi.convertColor(Colours.getColor(Color_element.Spoline, true)));
		colors.add(gi.convertColor(Colours.getColor(Color_element.Clenchline, true)));
		colors.add(gi.convertColor(Colours.getColor(Color_element.Alarm, true)));
		colors.add(gi.convertColor(Colours.getColor(Color_element.Text, true)));
		colors.add(gi.convertColor(Colours.getColor(Color_element.Stressline, true)));

		Sample_Correlation psc = samples.get(0);
		for(Sample_Correlation sc : samples) {
			if(sc.value<0 || sc.value>=colors.size()){
				gi.setColor(colors.get(0));
			}else {
				gi.setColor(colors.get((int) sc.value));
			}

			gi.drawLine(xtimescale(psc.time), height_low-(int)((slot_spacing/scale)*psc.value), xtimescale(sc.time), height_low-(int)((slot_spacing/scale)*sc.value));
		}


	}

	double clenching_spo2_corr = 0, clenching_hr_corr = 0, clenching_stress_corr = 0, noise_corr = 0, clenching_sleep_stage_deep_corr = 0, clenching_sleep_stage_light_corr = 0, clenching_sleep_stage_rem_corr = 0, clenching_sleep_stage_awake_corr = 0, accel_corr = 0;
	double raw_spo2_corr = 0, raw_hr_corr = 0, raw_stress_corr = 0;

	double[] clenching_night;
	void calculateCorrelations(){
		int samples = (int)Math.ceil((double) (max_time - min_time) /1000.0);

		// PREPARAZIONE DATI RAW SINCRONIZZATI
		ArrayList<Sample_Correlation> samples_raw = new ArrayList<>();
		if (raw_events != null && !raw_events.isEmpty()) {
			long syncmillis = -1;
			for (Event e : events) {
				if (e.type.equals("Sync")) {
					syncmillis = Long.valueOf(e.notes) - e.millis;
					break;
				}
			}
			if (syncmillis != -1) {
				for (RawEvent re : raw_events) {
					long corrected_time = re.millis - syncmillis;
					// Inseriamo solo i campioni validi per il grafico
					if (corrected_time >= min_time && corrected_time <= max_time) {
						// re.fvalue contiene il dato continuo
						samples_raw.add(new Sample_Correlation(corrected_time, (double) re.fvalue));
					}
				}
			}
		}

		// Dati Categorici / Binari (Usano l'Hold a gradino)
		Future<double[]> clenchFuture = taskRunner.submit(() -> createSampledArray(samples_clench, samples));
		Future<double[]> sleepFuture = taskRunner.submit(() -> createSampledArray(samples_sleepstages, samples));

		// Dati Biometrici Continui (Usano l'Interpolazione Lineare)
		Future<double[]> hrFuture = taskRunner.submit(() -> createLinearSampledArray(samples_hr, samples));
		Future<double[]> spo2Future = taskRunner.submit(() -> createLinearSampledArray(samples_spo2, samples));
		Future<double[]> stressFuture = taskRunner.submit(() -> createLinearSampledArray(samples_stress, samples));
		Future<double[]> noiseFuture = taskRunner.submit(() -> createLinearSampledArray(samples_noise, samples));
		Future<double[]> accelFuture = taskRunner.submit(() -> createLinearSampledArray(samples_accel, samples));

		Future<double[]> rawFuture = taskRunner.submit(() -> createLinearSampledArray(samples_raw, samples));

		// Later: wait for results
        try {
            clenching_night = clenchFuture.get();

        	double[] hr_night = hrFuture.get();
			double[] spo2_night = spo2Future.get();
			double[] stress_night = stressFuture.get();

			double[] sleepstages = sleepFuture.get();

			double[] noise_night = noiseFuture.get();

			double[] accel_night = accelFuture.get();

			double[] raw_night = rawFuture.get();

			double[] sleep_stage_rem_night = new double[samples];
			double[] sleep_stage_light_night = new double[samples];
			double[] sleep_stage_deep_night = new double[samples];
			double[] sleep_stage_awake_night = new double[samples];

			if (!samples_sleepstages.isEmpty()) {
				for(int i = 0; i<samples; i++) {
					double value = sleepstages[i];

					sleep_stage_rem_night[i] = (SleepData.REM == (int) value) ? 1.0 : 0.0;
					sleep_stage_awake_night[i] = (SleepData.AWAKE == (int) value) ? 1.0 : 0.0;
					sleep_stage_light_night[i] = (SleepData.LIGHT_SLEEP == (int) value) ? 1.0 : 0.0;
					sleep_stage_deep_night[i] = (SleepData.DEEP_SLEEP == (int) value) ? 1.0 : 0.0;
				}
			}

			Future<Double> raw_hr_corr_future = null, raw_spo2_corr_future = null, raw_stress_corr_future = null;
			if (!samples_raw.isEmpty()) {
				if (!samples_hr.isEmpty()) raw_hr_corr_future = taskRunner.submit(() -> Correlations.pearsonCorrelation(raw_night, hr_night));
				if (!samples_spo2.isEmpty()) raw_spo2_corr_future = taskRunner.submit(() -> Correlations.pearsonCorrelation(raw_night, spo2_night));
				if (!samples_stress.isEmpty()) raw_stress_corr_future = taskRunner.submit(() -> Correlations.pearsonCorrelation(raw_night, stress_night));
			}

			boolean draw_debug = false;
			double debugline = 0.5;
			double increment = 0.7;


			Future<Double> hr_corr_future = null, spo2_corr_future= null, stress_corr_future= null, noise_corr_future = null, deep= null, light= null, rem= null, awake= null, accel_corr_future = null;
			if (!samples_hr.isEmpty()) {
				hr_corr_future = taskRunner.submit(() -> Correlations.pearsonCorrelation(clenching_night, hr_night));
			}

			if (!samples_spo2.isEmpty()){
				spo2_corr_future = taskRunner.submit(() -> Correlations.pearsonCorrelation(clenching_night, spo2_night));
			}

			if (!samples_stress.isEmpty()){
				stress_corr_future = taskRunner.submit(() -> Correlations.pearsonCorrelation(clenching_night, stress_night));
			}

			if(!samples_noise.isEmpty()){
				noise_corr_future = taskRunner.submit(() -> Correlations.pearsonCorrelation(clenching_night, noise_night));
			}

			if (!samples_accel.isEmpty()){
				accel_corr_future = taskRunner.submit(() -> Correlations.pearsonCorrelation(clenching_night, accel_night));
			}

			if (!samples_sleepstages.isEmpty()) {

				deep = taskRunner.submit(() -> Correlations.pearsonCorrelation(clenching_night, sleep_stage_deep_night));
				light = taskRunner.submit(() -> Correlations.pearsonCorrelation(clenching_night, sleep_stage_light_night));
				rem = taskRunner.submit(() -> Correlations.pearsonCorrelation(clenching_night, sleep_stage_rem_night));
				awake = taskRunner.submit(() -> Correlations.pearsonCorrelation(clenching_night, sleep_stage_awake_night));
			}

			if (!samples_hr.isEmpty()) {
				clenching_hr_corr = hr_corr_future.get();

				delayed_corrs_hr = Correlations.calculateDelayedCorrelations(clenching_night, hr_night, 0, max_minute_correlation_delay*60, true);
				if(delayed_corrs_hr!=null){
					System.out.println("The highest correlation for spo2 is at " + delayed_corrs_hr.get(0).delay + " seconds ("+ delayed_corrs_hr.get(0).correlation + "). While the lowest is at " + delayed_corrs_hr.get(delayed_corrs_hr.size()-1).delay + " seconds (" + delayed_corrs_hr.get(delayed_corrs_hr.size()-1).correlation+").");
				}

				delayed_raw_corrs_hr = Correlations.calculateDelayedCorrelations(raw_night, hr_night, 0, max_minute_correlation_delay*60, true);
				if(delayed_raw_corrs_hr!=null){
					System.out.println("The highest RAW correlation for BPM is at " + delayed_raw_corrs_hr.get(0).delay + " seconds ("+ delayed_raw_corrs_hr.get(0).correlation + "). While the lowest is at " + delayed_raw_corrs_hr.get(delayed_raw_corrs_hr.size()-1).delay + " seconds (" + delayed_raw_corrs_hr.get(delayed_raw_corrs_hr.size()-1).correlation+").");
				}
			}

			if (!samples_spo2.isEmpty()){
				clenching_spo2_corr = spo2_corr_future.get();

				// We want a maximum delay of 60 mins for correlations.
				// Samples are 1 per second, so we want 60*60 samples

				delayed_corrs_spo2 = Correlations.calculateDelayedCorrelations(clenching_night, spo2_night, 0, max_minute_correlation_delay*60, true);
				if(delayed_corrs_spo2!=null){
					System.out.println("The highest correlation for spo2 is at " + delayed_corrs_spo2.get(0).delay + " seconds ("+ delayed_corrs_spo2.get(0).correlation + "). While the lowest is at " + delayed_corrs_spo2.get(delayed_corrs_spo2.size()-1).delay + " seconds (" + delayed_corrs_spo2.get(delayed_corrs_spo2.size()-1).correlation+").");
				}

				delayed_raw_corrs_spo2 = Correlations.calculateDelayedCorrelations(raw_night, spo2_night, 0, max_minute_correlation_delay*60, true);
				if(delayed_raw_corrs_spo2!=null){
					System.out.println("The highest RAW correlation for spo2 is at " + delayed_raw_corrs_spo2.get(0).delay + " seconds ("+ delayed_raw_corrs_spo2.get(0).correlation + "). While the lowest is at " + delayed_raw_corrs_spo2.get(delayed_raw_corrs_spo2.size()-1).delay + " seconds (" + delayed_raw_corrs_spo2.get(delayed_raw_corrs_spo2.size()-1).correlation+").");
				}
			}

			if (!samples_stress.isEmpty()){
				clenching_stress_corr = stress_corr_future.get();
			}

			if(!samples_noise.isEmpty()){
				noise_corr=noise_corr_future.get();
			}

			if (!samples_accel.isEmpty()){
				accel_corr = accel_corr_future.get();

				delayed_corrs_accel = Correlations.calculateDelayedCorrelations(clenching_night, accel_night, 0, 30*60, true);


			}

			if (!samples_sleepstages.isEmpty()) {

				clenching_sleep_stage_deep_corr = deep.get();
				clenching_sleep_stage_light_corr = light.get();
				clenching_sleep_stage_rem_corr = rem.get();
				clenching_sleep_stage_awake_corr = awake.get();

			}

			if (raw_hr_corr_future != null) raw_hr_corr = raw_hr_corr_future.get();
			if (raw_spo2_corr_future != null) raw_spo2_corr = raw_spo2_corr_future.get();
			if (raw_stress_corr_future != null) raw_stress_corr = raw_stress_corr_future.get();



			if(draw_debug) {
				drawSamplesLineDebug(clenching_night, "Clench", debugline += increment, 1);
				//drawSamplesArraysLineDebug(samples_clench, "Clench", debugline+=increment, 1);

				if (!samples_hr.isEmpty()) {
					drawSamplesLineDebug( hr_night, "HR", debugline+=increment, 100);
				}

				if (!samples_spo2.isEmpty()){
					drawSamplesLineDebug( spo2_night, "SpO2", debugline+=increment, 100);
					//drawSamplesArraysLineDebug(samples_spo2, "SPO2", debugline+=increment, 100.0);

				}

				if (!samples_stress.isEmpty()){
					drawSamplesLineDebug( stress_night, "Stress", debugline+=increment, 100);
					//drawSamplesArraysLineDebug(samples_stress, "Stress", debugline+=increment, 100.0);

				}

				if (!samples_sleepstages.isEmpty()) {
					//drawSamplesArraysLineDebug(samples_sleepstages, "Sleep", debugline+=increment, 5);

					drawSamplesLineDebug( sleep_stage_deep_night, "Deep", debugline+=increment, 1);
					drawSamplesLineDebug( sleep_stage_light_night, "Light", debugline+=increment, 1);
					drawSamplesLineDebug( sleep_stage_rem_night, "REM", debugline+=increment, 1);
					drawSamplesLineDebug( sleep_stage_awake_night, "AWAKE", debugline+=increment, 1);

				}

			}

			taskRunner.shutdown();

		} catch (ExecutionException e) {
			throw new RuntimeException(e);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

	}

	public float calculatePercentage(int value, int maxValue, int minValue) {
		// Calculate percentage of value from the baseline relative to minAverage
		return (float) (value - minValue) / (maxValue - minValue);
	}
	public int calculateHeightFromPercentage(float percentage, int HeightLow, int HeightHigh) {
		// Map percentage to height between the low and high values
		return (int) (HeightLow + ((HeightHigh - HeightLow) * percentage));
	}

	public void setStartUnixSeconds(String session_name) {

		for (Event e : events) {
			if (e.type.equals("Start")) {
				SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
				try {
					Date date = formatter.parse(session_name);
					Calendar calendar = Calendar.getInstance();
					calendar.setTime(date);
					calendar.set(Calendar.HOUR, Integer.parseInt(e.time.split(":")[0]));
					calendar.set(Calendar.MINUTE, Integer.parseInt(e.time.split(":")[1]));
					calendar.set(Calendar.SECOND, 0);
					sync_unix_second_time_start = (int)(calendar.getTimeInMillis()/1000.0);
					System.out.println("Session started at: " + calendar.getTime() + " : " + sync_unix_second_time_start);

				} catch (ParseException ee) {
					ee.printStackTrace();
				}
			}
			if (e.type.equals("UnixSeconds")) {
				sync_unix_second_time_start = Long.parseLong(e.notes);
				System.out.println("Session started (using UnixSec) at: " + sync_unix_second_time_start);

				break;
			}

		}

	}

	int sleepStageHeightConvert(int stagevalue) {
		int[] conversion = new int[] {4,4,2,1,3,4};
		if(stagevalue >= conversion.length)
			return 4;
		return conversion[stagevalue];
	}

	boolean drawSleepStageOutline(SleepData.SleepStage previous_stage, SleepData.SleepStage current_stage, long unixcorrection) {

		int sleepYstart = timeline_height;

		int sleepstagespacing = (2*slot_height)/4;

		boolean end_here = false;

		int prev_value;
		long prev_unix_sec;
		if(previous_stage!=null) {
			prev_value = previous_stage.value;
			prev_unix_sec = previous_stage.unix_sec_end;

			if(1000*(current_stage.unix_sec_end+unixcorrection) < min_time) {
				return true;
			}

		}else {
			prev_value = 1;
			prev_unix_sec = -unixcorrection;
		}

		if(prev_value==0)
			prev_value = 4;

		if(current_stage.value==0)
			current_stage.value = 4;


		long prev_end_ms = 1000*(prev_unix_sec+unixcorrection);
		long cur_end_ms = 1000*(current_stage.unix_sec_end+unixcorrection);

		if(prev_end_ms<min_time)prev_end_ms=min_time;
		if(prev_end_ms>max_time) {
			prev_end_ms=max_time;
		}

		if(cur_end_ms<min_time)cur_end_ms=min_time;

		if(cur_end_ms>max_time) {
			cur_end_ms=max_time;
		}


		String[] sleepcolors = new String[]{
				"#FDD835",	// Unknown
				"#FDD835",	// Awake
				"#4FC3F7",	// Light sleep
				"#0091EA",	// Deep sleep
				"#18FFFF",	// REM
				"#FDD835"	// Unknown
		};

		int prev_stage_height = sleepYstart-(sleepStageHeightConvert(prev_value)*sleepstagespacing);
		int cur_stage_height = sleepYstart-(sleepStageHeightConvert(current_stage.value)*sleepstagespacing);

		samples_sleepstages.add(new Sample_Correlation(prev_end_ms,current_stage.value));


		Color colorA, colorB = gi.convertColor(sleepcolors[current_stage.value]);
		colorA = sleepStageHeightConvert(prev_value)>sleepStageHeightConvert(current_stage.value) ? gi.convertColor(sleepcolors[prev_value]) : colorB;

		gi.setColor(colorA);
		gi.drawLine(xtimescale(prev_end_ms), prev_stage_height, xtimescale(prev_end_ms), cur_stage_height);
		gi.setColor(colorB);
		gi.drawLine(xtimescale(prev_end_ms), cur_stage_height, xtimescale(cur_end_ms), cur_stage_height);

		//if(previous_stage==null)
		//	drawEventLine(xtimescale(1000*(current_stage.unix_sec+unixcorrection)), "", -2, 5, false,
		//			gi.convertColor(sleepcolors[1]),
		//			gi.convertColor(sleepcolors[1]));
		//if(end_here)
		//	drawEventLine(xtimescale(cur_end_ms), "", 0, 4, false,
		//				gi.convertColor(sleepcolors[1]),
		//				gi.convertColor(sleepcolors[1]));

		return end_here;

	}

	void drawSleepStages(ArrayList<SleepData.SleepStage> data) {
		if(data.isEmpty())
			return;

		long unixcorrection = data.get(0).unix_sec-sync_unix_second_time_start;
		// Let's adjust the first item
		data.get(0).unix_sec = 0;

		samples_sleepstages.add(new Sample_Correlation(min_time, SleepData.AWAKE));

		SleepData.SleepStage previous = null;
		for(SleepData.SleepStage ss : data) {
			if(drawSleepStageOutline(previous, ss, unixcorrection)) {
				if (previous == null)
					continue;
				else
					break;
			}
			previous = ss;



		}

	}

	void drawNoise(String name, ArrayList<NoiseEvent> data, int height_high, int height_low, boolean use_dark_mode, ColorBands colorbands, int standard_minval, int standard_maxval, boolean drawRight, ArrayList<Sample_Correlation> fillsamples_array, boolean use_previous_color_if_increased) {

		if(data==null)
			return;

		if(data.isEmpty())
			return;


		// Initialize the baseline with a large value or Integer.MAX_VALUE
		double baseline = Integer.MAX_VALUE;
		double minFvalue = Integer.MAX_VALUE; // To store the minimum fvalue
		double maxFvalue = Integer.MIN_VALUE; // To store the maximum fvalue

		// Minimum average
		double avgFvalue = 0;
		double countValues = 0;

		// Iterate through the events array
		for (NoiseEvent event : data) {

			avgFvalue += event.db;
			countValues++;

			// Track the minimum and maximum value for events where value is false
			if (event.db < minFvalue) {
				minFvalue = event.db;
			}
			if (event.db > maxFvalue) {
				maxFvalue = event.db;
			}

		}
		if(countValues!=0)
			avgFvalue = avgFvalue/countValues;
		baseline = avgFvalue;

		gi.setColor(gi.convertColor(Colours.getColor(Color_element.Clenchline_guide, use_dark_mode)));
		gi.drawLine(xtimescale(min_time), height_high, xtimescale(max_time), height_high);
		gi.drawLine(xtimescale(min_time), height_low, xtimescale(max_time), height_low);

		if(!drawRight) {
			gi.drawString(String.valueOf(minFvalue), xtimescale(min_time) - 9 * 4, height_low + 7);
			gi.drawString(String.valueOf(avgFvalue), xtimescale(min_time) - 9 * 4, height_low - ((height_low-height_high)/2) + 7);
			gi.drawString(String.valueOf(maxFvalue), xtimescale(min_time) - 9 * 4, height_high + 7);

			gi.setColor(colorbands.getDefaultcolor());
			gi.drawString(name, xtimescale(min_time) - 9 * 10, height_low - ((height_low-height_high)/2) + 7);

		}else {
			gi.drawString(String.valueOf(minFvalue), xtimescale(max_time) + 9, height_low + 7);
			gi.drawString(String.valueOf(avgFvalue), xtimescale(max_time) + 9, height_low - ((height_low-height_high)/2) + 7);
			gi.drawString(String.valueOf(maxFvalue), xtimescale(max_time) + 9, height_high + 7);

			gi.setColor(colorbands.getDefaultcolor());
			gi.drawString(name, xtimescale(max_time) + (9*4), height_low - ((height_low-height_high)/2) + 7);

		}


		if(standard_maxval>0)
			maxFvalue = standard_maxval;
		if(standard_minval>0)
			minFvalue = standard_minval;


		NoiseEvent last_event = null;
		for (NoiseEvent re : data) {

			if (re.millis > max_time)
				continue;

			if (re.millis < min_time)
				continue;


			if (last_event == null) {
				last_event = re;
				continue;
			}

			gi.setColor(colorbands.getColorFromValue((int) (use_previous_color_if_increased && re.db>last_event.db ? last_event.db : re.db)));
			gi.drawLine(xtimescale(last_event.millis),
					calculateHeightFromPercentage(calculatePercentage((int)last_event.db, (int)maxFvalue, (int)minFvalue), height_low, height_high),
					xtimescale(re.millis),
					calculateHeightFromPercentage(calculatePercentage((int) re.db, (int)maxFvalue, (int)minFvalue), height_low, height_high));

			fillsamples_array.add(new Sample_Correlation(re.millis, re.db));


			last_event = re;
		}

		if(baseline != maxFvalue) {
			gi.setColor(gi.convertColor(Colours.getColor(Color_element.Clenching, use_dark_mode)));
			int baseline_line = calculateHeightFromPercentage(calculatePercentage((int) baseline, (int)maxFvalue, (int)minFvalue), height_low, height_high);
			//gi.drawLine(xtimescale(min_time), baseline_line, xtimescale(max_time), baseline_line);
		}
	}

	void drawSleepRecords(String name, ArrayList<SleepData.Record> data, int height_high, int height_low, boolean use_dark_mode, ColorBands colorbands, int standard_minval, int standard_maxval, boolean drawRight, ArrayList<Sample_Correlation> fillsamples_array, boolean use_previous_color_if_increased) {

		if(data.isEmpty())
			return;

		long unixcorrection = data.get(0).unix_sec-sync_unix_second_time_start;
		// Let's adjust the first item
		data.get(0).unix_sec = 0;

		// Initialize the baseline with a large value or Integer.MAX_VALUE
		int baseline = Integer.MAX_VALUE;
		int minFvalue = Integer.MAX_VALUE; // To store the minimum fvalue
		int maxFvalue = Integer.MIN_VALUE; // To store the maximum fvalue

		// Minimum average
		int avgFvalue = 0;
		int countValues = 0;

		// Iterate through the events array
		for (SleepData.Record event : data) {

			if (((event.unix_sec+unixcorrection)*1000) > max_time)
				continue;

			if (((event.unix_sec+unixcorrection)*1000) < min_time)
				continue;


			avgFvalue += event.value;
			countValues++;

			// Track the minimum and maximum value for events where value is false
			if (event.value < minFvalue) {
				minFvalue = event.value;
			}
			if (event.value > maxFvalue) {
				maxFvalue = event.value;
			}

		}
		if(countValues!=0)
			avgFvalue = avgFvalue/countValues;
		baseline = avgFvalue;

		gi.setColor(gi.convertColor(Colours.getColor(Color_element.Clenchline_guide, use_dark_mode)));
		gi.drawLine(xtimescale(min_time), height_high, xtimescale(max_time), height_high);
		gi.drawLine(xtimescale(min_time), height_low, xtimescale(max_time), height_low);

		if(!drawRight) {
			gi.drawString(String.valueOf(minFvalue), xtimescale(min_time) - 9 * 4, height_low + 7);
			gi.drawString(String.valueOf(avgFvalue), xtimescale(min_time) - 9 * 4, height_low - ((height_low-height_high)/2) + 7);
			gi.drawString(String.valueOf(maxFvalue), xtimescale(min_time) - 9 * 4, height_high + 7);

			gi.setColor(colorbands.getDefaultcolor());
			gi.drawString(name, xtimescale(min_time) - 9 * 10, height_low - ((height_low-height_high)/2) + 7);

		}else {
			gi.drawString(String.valueOf(minFvalue), xtimescale(max_time) + 9, height_low + 7);
			gi.drawString(String.valueOf(avgFvalue), xtimescale(max_time) + 9, height_low - ((height_low-height_high)/2) + 7);
			gi.drawString(String.valueOf(maxFvalue), xtimescale(max_time) + 9, height_high + 7);

			gi.setColor(colorbands.getDefaultcolor());
			gi.drawString(name, xtimescale(max_time) + (9*4), height_low - ((height_low-height_high)/2) + 7);

		}


		if(standard_maxval>0)
			maxFvalue = standard_maxval;
		if(standard_minval>0)
			minFvalue = standard_minval;


		SleepData.Record last_event = null;
		for (SleepData.Record re : data) {

			if (((re.unix_sec+unixcorrection)*1000) > max_time)
				continue;

			if (((re.unix_sec+unixcorrection)*1000) < min_time)
				continue;


			if (last_event == null) {
				last_event = re;
				continue;
			}

			boolean stop_drawing = ((re.unix_sec+unixcorrection)*1000) > max_time;

			if (((re.unix_sec+unixcorrection)*1000) > max_time)
				re.unix_sec = max_time/1000;

			if (((re.unix_sec+unixcorrection)*1000) < min_time) {
				last_event = re;
				continue;
			}

			gi.setColor(colorbands.getColorFromValue(use_previous_color_if_increased && re.value>last_event.value ? last_event.value : re.value));
			gi.drawLine(xtimescale(((last_event.unix_sec+unixcorrection)*1000)),
					calculateHeightFromPercentage(calculatePercentage(last_event.value, maxFvalue, minFvalue), height_low, height_high),
					xtimescale(((re.unix_sec+unixcorrection)*1000)),
					calculateHeightFromPercentage(calculatePercentage(re.value, maxFvalue, minFvalue), height_low, height_high));

			fillsamples_array.add(new Sample_Correlation((re.unix_sec+unixcorrection)*1000, re.value));

			if (stop_drawing)
				break;



			last_event = re;
		}

		if(baseline != maxFvalue) {
			gi.setColor(gi.convertColor(Colours.getColor(Color_element.Clenching, use_dark_mode)));
			int baseline_line = calculateHeightFromPercentage(calculatePercentage(baseline, maxFvalue, minFvalue), height_low, height_high);
			//gi.drawLine(xtimescale(min_time), baseline_line, xtimescale(max_time), baseline_line);
		}
	}

	void drawRaw(boolean use_dark_mode) {

		if (raw_events == null)
			return;

		boolean fallback_nofvalues = false;

		// Initialize the baseline with a large value or Integer.MAX_VALUE
		int baseline = Integer.MAX_VALUE;
		int minFvalue = Integer.MAX_VALUE; // To store the minimum fvalue
		int maxFvalue = Integer.MIN_VALUE; // To store the maximum fvalue

		// Minimum average
		int sumFvalue = 0;
		int countFalseValues = 0;

		// Iterate through the events array
		for (RawEvent event : raw_events) {
			if (event.value) {
				// If the event value is true, check if its fvalue is smaller than the current baseline
				if (event.fvalue < baseline) {
					baseline = event.fvalue;
				}
			} else {
				// If the event value is false, add the fvalue to the sum and increment the count
				sumFvalue += event.fvalue;
				countFalseValues++;
			}
			// Track the minimum and maximum fvalue for events where value is false
			if (event.fvalue < minFvalue) {
				minFvalue = event.fvalue;
			}
			if (event.fvalue > maxFvalue) {
				maxFvalue = event.fvalue;
			}
		}

		// Calculate the minimum average if there are events where value is false
		float minAverage = 0;
		if (countFalseValues > 0) {
			minAverage = (float) sumFvalue / countFalseValues;
		}

		fallback_nofvalues = baseline == 0;

		long syncmillis = -1;

		for (Event e : events) {
			if (e.type.equals("Sync")) {
				syncmillis = Long.valueOf(e.notes) - e.millis;
				break;
			}
		}

		if (syncmillis == -1) {
			System.out.println("Could not find the Sync tag, can't synchronize RAW data");
			return;
		}

		gi.setColor(gi.convertColor(Colours.getColor(Color_element.Clenchline_guide, use_dark_mode)));
		gi.drawLine(xtimescale(min_time), clenchline_height_high, xtimescale(max_time), clenchline_height_high);
		gi.drawLine(xtimescale(min_time), clenchline_height_low, xtimescale(max_time), clenchline_height_low);

		gi.drawString("Undetected", xtimescale(min_time) - 9 * 10, clenchline_height_low + 5);
		gi.drawString("Detected", xtimescale(min_time) - 9 * 10, clenchline_height_high + 5);

		gi.setColor(gi.convertColor(Colours.getColor(Color_element.Clenchline, use_dark_mode)));
		RawEvent last_event = null;
		for (RawEvent re : raw_events) {
			if (last_event == null) {
				last_event = re;
				continue;
			}

			boolean stop_drawing = (re.millis - syncmillis) > max_time;

			if ((re.millis - syncmillis) > max_time)
				re.millis = max_time;

			if ((re.millis - syncmillis) < min_time) {
				last_event = re;
				continue;
			}

			if(fallback_nofvalues)
				gi.drawLine(xtimescale(last_event.millis - syncmillis),
						(last_event.value ? clenchline_height_high : clenchline_height_low),
						xtimescale(re.millis - syncmillis), (re.value ? clenchline_height_high : clenchline_height_low));
			else
				gi.drawLine(xtimescale(last_event.millis - syncmillis),
						calculateHeightFromPercentage(calculatePercentage(last_event.fvalue, maxFvalue, minFvalue), clenchline_height_low, clenchline_height_high),
						xtimescale(re.millis - syncmillis),
						calculateHeightFromPercentage(calculatePercentage(re.fvalue, maxFvalue, minFvalue), clenchline_height_low, clenchline_height_high));

			if (stop_drawing)
				break;

			last_event = re;
		}

		if(baseline != maxFvalue) {
			gi.setColor(gi.convertColor(Colours.getColor(Color_element.Clenching, use_dark_mode)));
			int baseline_line = calculateHeightFromPercentage(calculatePercentage(baseline, maxFvalue, minFvalue), clenchline_height_low, clenchline_height_high);
			gi.drawLine(xtimescale(min_time), baseline_line, xtimescale(max_time), baseline_line);
		}
	}

	public void drawIcons(int graphX, int graphY) {
		int incremental = 0;
		int sessionincremental = 0;

		// Collect these to print by niceness
		ArrayList<IconAndNiceness> infoicons = new ArrayList<>();

		Image androidIcon = null;
		Image moodIcon = null;
		ArrayList<IconAndNiceness> sessionicons = new ArrayList<>();

		for (Event e : events) {
			//System.out.println("Event Type: " + e.type);
			if (e.type.equalsIgnoreCase("android") && androidIcon==null) {
				androidIcon =  icons.get("android").icon;
			}

			if (e.type.equalsIgnoreCase("info")) {
				// Splitting by comma handles cases like "Alcohol,Life Event,Caffeine"
				String[] splitNotes = e.notes.split(",");
				for (String note : splitNotes) {
					String noteKey = note.trim().toLowerCase();
					IconAndNiceness ian = icons.get(noteKey);
					if(ian==null) {
						System.out.println("Info icon is null: " + noteKey);

					} else {
						infoicons.add(ian);
					}
				}



			}

			if (e.type.equalsIgnoreCase("session")) {
				String noteKey = e.notes.trim().toLowerCase();
				IconAndNiceness ian = icons.get(noteKey);
				if(ian==null) {
					System.out.println("Session icon is null: " + noteKey);

				} else {
					sessionicons.add(ian);
				}


			}

			if (e.type.equalsIgnoreCase("mood") && moodIcon==null) {
				String noteKey = e.notes.trim().toLowerCase();
				IconAndNiceness ian = icons.get(noteKey);
				if(ian==null) {
					System.out.println("Mood icon is null: " + noteKey);

				} else {
					moodIcon = ian.icon;
				}



			}
		}

		if(androidIcon!=null) {
			drawIconToSessionGrid(sessionincremental++, androidIcon, graph_width - 60, 30);
		}
		if(moodIcon!=null) {
			drawIconToSessionGrid(sessionincremental++, moodIcon, graph_width - 60, 30);
		}

		String [] nicenesses = {Bad, Mediocre, Neutral, Nice};
		// Loop for niceness levels
		for(int i = nicenesses.length-1; i>=0; i--)
			for(IconAndNiceness e : sessionicons) {
				if(!e.niceness.equals(nicenesses[i])) {continue;}
				drawIconToSessionGrid(sessionincremental++, e.icon, graph_width - 60, 30);
			}

		// Loop for niceness levels
		for(int i = nicenesses.length-1; i>=0; i--)
			for(IconAndNiceness e : infoicons) {
				if(!e.niceness.equals(nicenesses[i])) {continue;}

				Image icon = e.icon;

				drawIconToGrid(incremental++, icon, graphX-(20), graphY);
			}
	}

	void drawIconToGrid(int iconincremental, Image icon, int graphX, int graphY) {
		int iconSize = 32;
		int spacing = 10;
		int startY = graphY;
		int startX = graphX;


		int numcolumns = 6;

		int row = iconincremental/numcolumns;
		int column = iconincremental%numcolumns;

		int x = startX-(column*(spacing+iconSize));
		int y = startY+(row*(spacing+iconSize));

		if (icon != null) {
			gi.drawImage(icon, x, y, iconSize, iconSize);
		}

	}

	void drawIconToSessionGrid(int iconincremental, Image icon, int graphX, int graphY) {
		int iconSize = 40;
		int spacing = 10;
		int startY = graphY;
		int startX = graphX-spacing;

		int numcolumns = 1;

		int row = iconincremental/numcolumns;
		int column = iconincremental%numcolumns;

		int x = startX-(column*(spacing+iconSize));
		int y = startY+(row*(spacing+iconSize));

		if (icon != null) {
			gi.drawImage(icon, x, y, iconSize, iconSize);
		}

	}

	void drawResets(boolean use_dark_mode){
		long lastmillis = 0;
		for(Event e : events){
			if(e.type.equals("ResetDetectedStartMs")){
				lastmillis = Long.parseLong(e.notes);
			}else if(e.type.equals("ResetDetectedEndMs")){
				long endmillis = Long.parseLong(e.notes);
				drawDurationRectangle(lastmillis, endmillis, 1, "Arduino down", gi.convertColor(Colours.getColor(Color_element.ResetBlock, use_dark_mode)),gi.convertColor(Colours.getColor(Color_element.ResetBlock, use_dark_mode)),gi.convertColor(Colours.getColor(Color_element.ResetBlock, use_dark_mode)),2);
			}
		}
	}
	void drawInfoStats(ArrayList<String> values, int rows, boolean use_dark_mode) {
		boolean ignoredate = true;

		gi.setColor(gi.convertColor(Colours.getColor(Color_element.Text, use_dark_mode)));

		int[] maxchars_row = new int[values.size()];
		Arrays.fill(maxchars_row, 0);

		int row_spacing = 20;

		int element_count = 0;

		for(String v : values) {
			int current_column = (((element_count)/rows));
			if(!ignoredate) {
				if(v.length()>maxchars_row[current_column]) {
					maxchars_row[current_column]=v.length();
				}
				if((element_count%rows)==0) {
					element_count++;
				}
			}else {
				ignoredate=false;
			}

			gi.drawString(v, side_info_margin + columnOffset(maxchars_row, current_column), info_text_height + (row_spacing * (element_count%rows)));
			element_count++;
		}
	}

	int columnOffset(int[] maxchars_row, int cc) {
		int sum = 0;
		for(int i = 1; i <= cc; i++) {
			sum+=maxchars_row[i-1]*7;
		}
		return sum;
	}

	public Event findStart() {
		for (Event e : events) {
			if(e.type.equals("Start"))
				return e;
		}
		return null;
	}


	public String findSessionName(){
		String[] startnote = findStart().notes.split(" ");
		return startnote[startnote.length-1]; // It's a string date YYYY-MM-DD;
	}

	// OVERLOAD per le correlazioni base (semplici double)
	boolean printInfoIfMeaningful(ArrayList<String> info, String beforevalue, Double value, String aftervalue) {
		if (value != null && !Double.isNaN(value) && Math.abs(value) >= THRESHOLD_CORRELATION_MIN) {
			info.add(beforevalue + ((int)(value * 100.0)) / 100.0 + aftervalue);
			return true;
		}
		return false;
	}

	// Il tuo metodo esistente per i CorrelationPair (con p-value e E%)
	boolean printInfoIfMeaningful(ArrayList<String> info, String beforevalue, Correlations.CorrelationPair result, String aftervalue) {
		if (result == null || Double.isNaN(result.correlation)) return false;

		// Applica le soglie unificate
		if (Math.abs(result.correlation) >= THRESHOLD_CORRELATION_MIN &&
				result.pValue <= THRESHOLD_P_VALUE_MAX &&
				result.effectSize >= THRESHOLD_EFFECT_SIZE_MIN) {

			// Stampa anche il p-value e l'Effect Size
			String statsString = String.format(Locale.ENGLISH, " (p:%.3f, E:%.1f%%)", result.pValue, result.effectSize);
			info.add(beforevalue + ((int) (result.correlation * 100.0)) / 100.0 + aftervalue + statsString);
			return true;
		}
		return false;
	}

	class ColorBands{
		public ColorBands(Color defaultcolor){
		this.defaultcolor=defaultcolor;
		}

		Color defaultcolor;

		public void addColorBand(BandColor bc){
			colors.add(bc);
		}
		ArrayList<BandColor> colors = new ArrayList<>();
		public Color getDefaultcolor(){return defaultcolor;}
		public Color getColorFromValue(int value){
			for(BandColor bc : colors) {
				if (value >= bc.min && value <= bc.max)
					return bc.getColor();
			}
			return defaultcolor;
		}

	};
	class BandColor{
		public BandColor(int min, int max, Color_element color, boolean use_dark_mode){
			this.min = min;
			this.max = max;
			this.color = color;
			this.use_dark_mode = use_dark_mode;
		}
		public Color getColor(){return gi.convertColor(Colours.getColor(color, use_dark_mode));}
		int min, max;
		boolean use_dark_mode;
		Color_element color;
	};
	ColorBands spocolors;
	ColorBands hrcolors;
	ColorBands stresscolors;


	public Image generateGraph(boolean use_dark_mode) {

		if(gi==null)
			throw new NullPointerException("You did not call platformSpecificAbstractions() before generating the graph");

		String session_name = findSessionName();
		setStartUnixSeconds(session_name);

		// Dark mode background
		gi.setColor(gi.convertColor(Colours.getColor(Color_element.Background, use_dark_mode)));
		gi.fillRect(0, 0, graph_width, graph_height);

		gi.setColor(gi.convertColor(Colours.getColor(Color_element.Text, use_dark_mode)));
		gi.setFont("Arial", 16);

		gi.drawLine(xtimescale(min_time), timeline_height, xtimescale(max_time), timeline_height);

		drawTimeTick(events.get(0).millis, events.get(0).time, 0, false,
				gi.convertColor(Colours.getColor(Color_element.Text, use_dark_mode)),
				gi.convertColor(Colours.getColor(Color_element.Text, use_dark_mode)));

		drawTimeTick(events.get(events.size() - 1).millis, events.get(events.size() - 1).time, 2, true,
				gi.convertColor(Colours.getColor(Color_element.Text, use_dark_mode)),
				gi.convertColor(Colours.getColor(Color_element.Text, use_dark_mode)));

		drawTimeBaseTick(events.get(0).millis, events.get(0).time, events.get(events.size() - 1).millis);


		int startx_legend = graph_width / 8;
		int y_legend = legend_height;
		int spacing = 300;

		gi.setColor(gi.convertColor(Colours.getColor(Color_element.Button, use_dark_mode)));
		gi.fillRect(startx_legend + (spacing * 0), y_legend, 20, 20);
		gi.drawString("Button", startx_legend + (spacing * 0) + 30, y_legend + 15);

		gi.setColor(gi.convertColor(Colours.getColor(Color_element.Warning, use_dark_mode)));
		gi.fillRect(startx_legend + (spacing * 1), y_legend, 20, 20);
		gi.drawString("Beep", startx_legend + (spacing * 1) + 30, y_legend + 15);

		gi.setColor(gi.convertColor(Colours.getColor(Color_element.Clenching, use_dark_mode)));
		gi.fillRect(startx_legend + (spacing * 2), y_legend, 20, 20);
		gi.drawString("Clenching", startx_legend + (spacing * 2) + 30, y_legend + 15);

		gi.setColor(gi.convertColor(Colours.getColor(Color_element.Alarm, use_dark_mode)));
		gi.fillRect(startx_legend + (spacing * 3), y_legend, 20, 20);
		gi.drawString("Alarm", startx_legend + (spacing * 3) + 30, y_legend + 15);

		drawIcons(graph_width - 100, 35);

		// Mark not clenching initially for correlations later
		samples_clench.add(new Sample_Correlation(min_time, 0.0));

		int c = 0, cc = 1;
		long last_beep = 0, last_button = 0, last_alarm = 0, last_clench = 0, last_alarm_stop = 0;
		int countbeeps = 0;
		long lastbeepwrite = 0;
		Event le = null;
		for (Event e : events) {

			if (countbeeps != 0 && !e.type.equals("Beep")) {

				if (le.millis - lastbeepwrite < findmsfromchars(2))
					cc++;
				else {
					cc = 1;
					lastbeepwrite = le.millis;
				}

				gi.setFont("Arial", 14);
				gi.setColor(gi.convertColor(Colours.getColor(Color_element.Warning, use_dark_mode)));
				gi.drawString(String.valueOf(countbeeps), xtimescale(le.millis), timeline_height + (14 * cc));

				gi.setFont("Arial", 16);
				countbeeps = 0;
			}

			switch (e.type) {

				case "Beep":
					countbeeps++;
					//drawEventLine(e.millis, (e.millis - last_beep > findmsfromchars(5) ? e.time : ""), beep_slot, beep_slot+beep_slot_length, false,
					//		gi.convertColor(Colours.getColor(Color_element.Warning, use_dark_mode)),
					//		gi.convertColor(Colours.getColor(Color_element.Text, use_dark_mode)));

					last_beep = e.millis;

					break;

				case "Alarm":
					if (e.notes.equals("STARTED")) {
						drawEventLine(e.millis, e.time, alarm_slot, alarm_slot+alarm_slot_length, false,
								gi.convertColor(Colours.getColor(Color_element.Alarm, use_dark_mode)),
								gi.convertColor(Colours.getColor(Color_element.Text, use_dark_mode)));
						last_alarm = e.millis;
					} else {
						last_alarm_stop = e.millis;
					}
					break;

				case "Button":
					drawEventLine(e.millis, "", button_slot, button_slot+button_slot_length, false, gi.convertColor(Colours.getColor(Color_element.Button, use_dark_mode)),
							gi.convertColor(Colours.getColor(Color_element.Text, use_dark_mode)));
					last_button = e.millis;
					break;

				case "Clenching":

					if (e.notes.equals("STARTED")) {
						if (e.millis - last_clench > 60000 * 10)
							c = 0;
						last_clench = e.millis;

						// Mark clenching for correlations later
						samples_clench.add(new Sample_Correlation(e.millis, 1.0));

						//drawEventLine(e.millis, "", 2, 1, false,
						//		gi.convertColor(Colours.getColor(Color_element.Clenching, use_dark_mode)),
						//		gi.convertColor(Colours.getColor(Color_element.Text, use_dark_mode)));
					} else {

						double duration = (double) (e.duration - ((last_alarm_stop == e.millis) ? 0 : 4));
						duration = (int) (duration * 10.0) / 10.0;
						// double duration = e.duration;
						String d = duration + "s";

						// Mark not clenching for correlations later
						samples_clench.add(new Sample_Correlation(e.millis, 0.0));

						drawDurationRectangle(last_clench, e.millis, clenching_slot, (duration < 1) ? "" : d,
								gi.convertColor(Colours.getColor(Color_element.Clenching, use_dark_mode)),
								gi.convertColor(Colours.getColor(Color_element.Text, use_dark_mode)),
								gi.convertColor(Colours.getColor(Color_element.Clenching, use_dark_mode)), (duration < 1) ? 0 : c++ % 25);
					}
					break;
				default:
					continue;
			}

			le = e;
		}

		gi.setColor(gi.convertColor(Colours.getColor(Color_element.Text, use_dark_mode)));
		gi.setFont("Arial", 14);
		gi.drawString("The data presented here has been corrected (-8s) for events that don't end with the alarm. Clenching events which lasted less than 1s are only drawn as red lines.",
				side_info_margin, graph_height - 12);


		drawResets(use_dark_mode);


		// Session info table

		sd = getStats();
		ArrayList<String> infostats = new ArrayList<>(Arrays.asList(new String[]{
				"Date: " + session_name + " Filename: " + file_name,
				"Duration: " + sd.getItem("Duration").split(":")[0] + "h " + sd.getItem("Duration").split(":")[1] + "m",
				"Warnings: " + sd.getItem("Beep Count"),
				"Alarms: " + sd.getItem("Alarm Triggers"),
				"Stop After Beeps: " + sd.getItem("Stopped after beep"),
				"Clenching Events: " + sd.getItem("Jaw Events"),
				"Avg beeps per event: " + sd.getItem("Avg beeps per event") + (Double.parseDouble(sd.getItem("Avg beeps per event")) <= 2.0 ? " <-- Cool!" : ""),

				"Total clenching time: " + sd.getItem("Total clench time (seconds)") + "s",
				"Clenching Rate: " + String.format(Locale.ENGLISH, "%.2f", Double.valueOf(sd.getItem("Clenching Rate (per hour)"))) + " /h",
				"Average pauses: " + sd.getItem("Average clenching event pause (minutes)") + "m",
				"Average clench duration: " + sd.getItem("Average clenching duration (seconds)") + "s" + (Double.parseDouble(sd.getItem("Average clenching duration (seconds)")) <= 5.0 ? " <-- Remarkable!" : ""),
				"Alarm percentage: " + sd.getItem("Alarm %") + "%",
				"Stop After Beeps %: " + sd.getItem("Stopped after beep %") + "%" + (Double.parseDouble(sd.getItem("Stopped after beep %")) > 95.0 ? " <-- Awesome!" : ""),

				"Active time: " + sd.getItem("Active time (permille)") + "‰"
		}));

		drawRaw(use_dark_mode);

		drawNoise("Noise", noise_events, noise_height_high, noise_height_low, use_dark_mode,new ColorBands(gi.convertColor(Colours.getColor(Color_element.Spoline, use_dark_mode))),-1,-1,false, samples_noise, true);
		drawNoise("Accel", accel_mag_events, noise_height_high, noise_height_low, use_dark_mode,new ColorBands(gi.convertColor(Colours.getColor(Color_element.Stressline, use_dark_mode))),-1,-1,true, samples_accel, true);

		if(!sleepData.sleep_stages.isEmpty()) {
			drawSleepStages(sleepData.sleep_stages);

			spocolors = new ColorBands(gi.convertColor(Colours.getColor(Color_element.Spoline, use_dark_mode)));
			hrcolors = new ColorBands(gi.convertColor(Colours.getColor(Color_element.Hrline, use_dark_mode)));
			stresscolors = new ColorBands(gi.convertColor(Colours.getColor(Color_element.Stressline, use_dark_mode)));

			spocolors.addColorBand(new BandColor(95,100,Color_element.Spoline, use_dark_mode));
			spocolors.addColorBand(new BandColor(88,95,Color_element.Spoline_warning, use_dark_mode));
			spocolors.addColorBand(new BandColor(0,88,Color_element.Spoline_danger, use_dark_mode));

			hrcolors.addColorBand(new BandColor(0,300,Color_element.Hrline, use_dark_mode));

			stresscolors.addColorBand(new BandColor(0,30,Color_element.Stressline, use_dark_mode));
			stresscolors.addColorBand(new BandColor(30,100,Color_element.Spoline_warning, use_dark_mode));

			drawSleepRecords("BPM", sleepData.heartrate,heartrate_height_high, heartrate_height_low, use_dark_mode, hrcolors, 30, -1,false, samples_hr, true);
			drawSleepRecords("SpO2", sleepData.spo2,spo2_height_high, spo2_height_low, use_dark_mode, spocolors, 0, 100,false, samples_spo2, true);
			drawSleepRecords("Stress", sleepData.stress,heartrate_height_high, heartrate_height_low, use_dark_mode, stresscolors, 0, 100,true, samples_stress, true);

			calculateCorrelations();

			int p1 = (int)(100.0f*calculatePercentage(sleepData.duration_lightsleep, sleepData.duration_sleep, 0)), p2 = (int)(100.0f*calculatePercentage(sleepData.duration_deepsleep, sleepData.duration_sleep, 0)), p3 = (int)(100.0f*calculatePercentage(sleepData.duration_rem, sleepData.duration_sleep, 0));

			infostats.add("Sleep duration: " + sleepData.duration_sleep/60 + "h "+ sleepData.duration_sleep%60 +"m");
			infostats.add("Light sleep: " + sleepData.duration_lightsleep/60 + "h "+ sleepData.duration_lightsleep%60 +"m (" + (p1) +"%)");
			infostats.add("Deep sleep: " + sleepData.duration_deepsleep/60 + "h "+ sleepData.duration_deepsleep%60 +"m (" + (p2) +"%)");
			infostats.add("REM: " + sleepData.duration_rem/60 + "h "+ sleepData.duration_rem%60 +"m (" + (p3) +"%)");
			infostats.add("Awake: " + sleepData.duration_awake/60 + "h "+ sleepData.duration_awake%60 +"m ("+sleepData.awake_count+")");

			if(sleepData.average_hr > 0)
			infostats.add("Average BPM: " + sleepData.average_hr);

			if(sleepData.average_breath_quality > 0)
			infostats.add("Breath Quality: " + sleepData.average_breath_quality + "%" );

			printInfoIfMeaningful(infostats, "Correlation with BPM: ", clenching_hr_corr, "");
			printInfoIfMeaningful(infostats, "Correlation with SpO2: ", clenching_spo2_corr, "");
			printInfoIfMeaningful(infostats, "Correlation with Stress: ", clenching_stress_corr, "");
			printInfoIfMeaningful(infostats, "Correlation with Awake: ", clenching_sleep_stage_awake_corr, "");
			printInfoIfMeaningful(infostats, "Correlation with Light Sleep: ", clenching_sleep_stage_light_corr, "");
			printInfoIfMeaningful(infostats, "Correlation with Deep Sleep: ", clenching_sleep_stage_deep_corr, "");
			printInfoIfMeaningful(infostats, "Correlation with REM: ", clenching_sleep_stage_rem_corr, "");

			if(delayed_corrs_hr != null && !delayed_corrs_hr.isEmpty()){
				Correlations.CorrelationPair highestPos = delayed_corrs_hr.get(0);
				Correlations.CorrelationPair highestNeg = delayed_corrs_hr.get(delayed_corrs_hr.size()-1);

				// Stampa usando il nuovo metodo per i CorrelationPair (aggiunge p-value e E%)
				printInfoIfMeaningful(infostats, "Highest BPM corr: ", highestPos, " at " + (highestPos.delay/60) + " min");
				printInfoIfMeaningful(infostats, "Highest negative BPM corr: ", highestNeg, " at " + (highestNeg.delay/60) + " min");
			}

			if(delayed_corrs_spo2 != null && !delayed_corrs_spo2.isEmpty()){
				Correlations.CorrelationPair highestPos = delayed_corrs_spo2.get(0);
				Correlations.CorrelationPair highestNeg = delayed_corrs_spo2.get(delayed_corrs_spo2.size()-1);

				// Stampa usando il nuovo metodo per i CorrelationPair (aggiunge p-value e E%)
				printInfoIfMeaningful(infostats, "Highest SpO2 corr: ", highestPos, " at " + (highestPos.delay/60) + " min");
				printInfoIfMeaningful(infostats, "Highest negative SpO2 corr: ", highestNeg, " at " + (highestNeg.delay/60) + " min");
			}

			// Stampa Correlazioni su Dati RAW (SVM Confidenza / Intensità)
			if (raw_events != null && !raw_events.isEmpty()) {
				printInfoIfMeaningful(infostats, "(no delay) Raw Signal Corr BPM: ", raw_hr_corr, "");
				printInfoIfMeaningful(infostats, "(no delay) Raw Signal Corr SpO2: ", raw_spo2_corr, "");
				printInfoIfMeaningful(infostats, "(no delay) Raw Signal Corr Stress: ", raw_stress_corr, "");

				if(delayed_raw_corrs_hr != null && !delayed_raw_corrs_hr.isEmpty()){
					Correlations.CorrelationPair highestPos = delayed_raw_corrs_hr.get(0);
					Correlations.CorrelationPair highestNeg = delayed_raw_corrs_hr.get(delayed_raw_corrs_hr.size()-1);

					// Stampa usando il nuovo metodo per i CorrelationPair (aggiunge p-value e E%)
					boolean H = printInfoIfMeaningful(infostats, "Highest RAW BPM corr: ", highestPos, " at " + (highestPos.delay/60) + " min");
					boolean L = printInfoIfMeaningful(infostats, "Highest negative RAW BPM corr: ", highestNeg, " at " + (highestNeg.delay/60) + " min");

					if(H||L) drawCorrelationProfileGraph(delayed_raw_corrs_hr, "RAW BPM CCF", side_info_margin, graph_height - 515, graph_width - (2 * side_info_margin), 25, use_dark_mode);

				}

				if(delayed_raw_corrs_spo2 != null && !delayed_raw_corrs_spo2.isEmpty()){
					Correlations.CorrelationPair highestPos = delayed_raw_corrs_spo2.get(0);
					Correlations.CorrelationPair highestNeg = delayed_raw_corrs_spo2.get(delayed_raw_corrs_spo2.size()-1);

					// Stampa usando il nuovo metodo per i CorrelationPair (aggiunge p-value e E%)
					boolean H = printInfoIfMeaningful(infostats, "Highest RAW SpO2 corr: ", highestPos, " at " + (highestPos.delay/60) + " min");
					boolean L = printInfoIfMeaningful(infostats, "Highest negative RAW SpO2 corr: ", highestNeg, " at " + (highestNeg.delay/60) + " min");

					if(H||L) drawCorrelationProfileGraph(delayed_raw_corrs_spo2, "RAW SpO2 CCF", side_info_margin, graph_height - 495, graph_width - (2 * side_info_margin), 25, use_dark_mode);

				}
			}

		}

		if(noise_corr != 0){
			printInfoIfMeaningful(infostats, "Noise corr: ", noise_corr, "");
		}

		if(accel_corr != 0){
			printInfoIfMeaningful(infostats, "Accel corr: ", accel_corr, "");
			if(delayed_corrs_accel != null && !delayed_corrs_accel.isEmpty()){
				Correlations.CorrelationPair highestPosAccel = delayed_corrs_accel.get(0);
				Correlations.CorrelationPair highestNegAccel = delayed_corrs_accel.get(delayed_corrs_accel.size()-1);

				printInfoIfMeaningful(infostats, "Highest accel corr: ", highestPosAccel, " at " + (highestPosAccel.delay/60) + " min");
				printInfoIfMeaningful(infostats, "Highest negative accel corr: ", highestNegAccel, " at " + (highestNegAccel.delay/60) + " min");

			}
		}

		drawInfoStats(infostats, 7, use_dark_mode);

		return gi.getImage();
	}

	public StatData getStats() {
		if(events == null) {
			throw new NullPointerException("You did not provide events for this file!");
		}
		if(sd==null) {
			String session_name = findSessionName();
			sd = Statistics.calcStats(session_name, events);
		}
		return sd;
	}

	public boolean writeImage(Image img, String file_name) {
		return gi.writeImage(img, file_name);
	}

	public void addRawData(ArrayList<RawEvent> raw_events) {
		this.raw_events = raw_events;
	}

	void drawCorrelationProfileGraph(
			ArrayList<Correlations.CorrelationPair> pairs,
			String title,
			int x,
			int y,
			int width,
			int height,
			boolean use_dark_mode) {

		if (pairs == null || pairs.isEmpty()) return;

		// =========================================================
		// LAYOUT
		// =========================================================

		final int textWidth = 85;
		final int gap = 6;
		final int paddingY = 3;

		int chartX = x + textWidth + gap;
		int chartWidth = width - textWidth - gap;

		if (chartWidth <= 10 || height <= 10) return;

		int chartTop = y + paddingY;
		int chartBottom = y + height - paddingY;
		int chartHeight = chartBottom - chartTop;

		int centerY = chartTop + chartHeight / 2;

		// =========================================================
		// TITOLO
		// =========================================================

		gi.setColor(gi.convertColor(
				Colours.getColor(Color_element.Text, use_dark_mode)));

		gi.setFont("Arial", 11);

		gi.drawString(
				title,
				x,
				centerY
		);

		// =========================================================
		// RISOLUZIONE
		//
		// 1 sample = 1 secondo
		// max_minute_correlation_delay = minuti
		// =========================================================

		final int maxDelaySamples =
				max_minute_correlation_delay * 60;

		if (maxDelaySamples <= 0) return;

		final int totalDelaySamples =
				maxDelaySamples * 2;

		// =========================================================
		// SLOT
		// =========================================================

		int numberOfCorrelations = pairs.size();

		if (numberOfCorrelations <= 0) return;

		double slotWidth =
				(double) chartWidth / numberOfCorrelations;

		// =========================================================
		// SCALA VERTICALE
		//
		// Pearson [-1, +1]
		// =========================================================

		int scaleHeight =
				Math.max(1, chartHeight / 2 - 2);

		// =========================================================
		// TROVA IL MASSIMO
		//
		// Cerchiamo il massimo in valore assoluto:
		//
		//   +0.40 > +0.20
		//   -0.50 > +0.40  <-- viene scelto -0.50
		//
		// Ignoriamo eventuali NaN.
		// =========================================================

		Correlations.CorrelationPair maxPair = null;

		double maxAbsoluteCorrelation = -1.0;

		for (Correlations.CorrelationPair cp : pairs) {

			if (cp.delay < -maxDelaySamples
					|| cp.delay > maxDelaySamples) {
				continue;
			}

			if (Double.isNaN(cp.correlation)
					|| Double.isInfinite(cp.correlation)) {
				continue;
			}

			double absoluteCorrelation =
					Math.abs(cp.correlation);

			if (absoluteCorrelation > maxAbsoluteCorrelation) {

				maxAbsoluteCorrelation = absoluteCorrelation;
				maxPair = cp;
			}
		}

		// =========================================================
		// ASSI
		// =========================================================

		gi.setColor(gi.convertColor(
				Colours.getColor(
						Color_element.Clenchline_guide,
						use_dark_mode)));

		// Pearson = 0
		gi.drawLine(
				chartX,
				centerY,
				chartX + chartWidth,
				centerY
		);

		// Delay = 0
		int zeroX =
				chartX + chartWidth / 2;

		gi.drawLine(
				zeroX,
				chartTop,
				zeroX,
				chartBottom
		);

		// =========================================================
		// POSIZIONE DEL MASSIMO
		//
		// La calcoliamo una volta sola.
		// =========================================================

		int maxPosX = -1;
		int maxPosY = -1;

		if (maxPair != null) {

			double normalizedMaxDelay =
					(double) (maxPair.delay + maxDelaySamples)
							/ totalDelaySamples;

			int maxSlot =
					(int) Math.floor(
							normalizedMaxDelay
									* numberOfCorrelations
					);

			if (maxSlot < 0) {
				maxSlot = 0;
			}

			if (maxSlot >= numberOfCorrelations) {
				maxSlot = numberOfCorrelations - 1;
			}

			maxPosX =
					chartX
							+ (int) ((maxSlot + 0.5) * slotWidth);

			double maxCorrelation =
					Math.max(
							-1.0,
							Math.min(1.0, maxPair.correlation)
					);

			maxPosY =
					centerY
							- (int) (maxCorrelation * scaleHeight);
		}

		// =========================================================
		// CCF
		// =========================================================

		for (Correlations.CorrelationPair cp : pairs) {

			// -----------------------------------------------------
			// Delay espresso in sample
			// -----------------------------------------------------

			if (cp.delay < -maxDelaySamples
					|| cp.delay > maxDelaySamples) {
				continue;
			}

			// -----------------------------------------------------
			// NORMALIZZAZIONE DEL DELAY
			// -----------------------------------------------------

			double normalizedDelay =
					(double) (cp.delay + maxDelaySamples)
							/ totalDelaySamples;

			// -----------------------------------------------------
			// SLOT
			// -----------------------------------------------------

			int slot =
					(int) Math.floor(
							normalizedDelay
									* numberOfCorrelations
					);

			if (slot < 0) {
				slot = 0;
			}

			if (slot >= numberOfCorrelations) {
				slot = numberOfCorrelations - 1;
			}

			// Centro dello slot
			int posX =
					chartX
							+ (int) ((slot + 0.5) * slotWidth);

			// -----------------------------------------------------
			// CORRELAZIONE
			// -----------------------------------------------------

			double correlation =
					Math.max(
							-1.0,
							Math.min(1.0, cp.correlation)
					);

			int posY =
					centerY
							- (int) (correlation * scaleHeight);

			// -----------------------------------------------------
			// COLORE
			// -----------------------------------------------------

			boolean statisticallyRelevant =
					Math.abs(cp.correlation)
							>= THRESHOLD_CORRELATION_MIN
							&& cp.pValue <= THRESHOLD_P_VALUE_MAX;

			boolean effectRelevant =
					cp.effectSize >= THRESHOLD_EFFECT_SIZE_MIN;

			if (statisticallyRelevant && effectRelevant) {

				gi.setColor(gi.convertColor(
						cp.correlation >= 0
								? Colours.getColor(
								Color_element.Clenching,
								use_dark_mode)
								: Colours.getColor(
								Color_element.Alarm,
								use_dark_mode)
				));

			} else {

				gi.setColor(gi.convertColor(
						Colours.getColor(
								Color_element.Clenchline_guide,
								use_dark_mode)
				));
			}

			// -----------------------------------------------------
			// BARRA CCF
			// -----------------------------------------------------

			gi.drawLine(
					posX,
					centerY,
					posX,
					posY
			);
		}

		// =========================================================
		// MARCATORE DEL MASSIMO
		// =========================================================

		if (maxPair != null && maxPosX >= 0) {

			// -----------------------------------------------------
			// Linea verticale sul picco
			// -----------------------------------------------------

			gi.setColor(gi.convertColor(
					Colours.getColor(
							Color_element.Text,
							use_dark_mode)));

			gi.drawLine(
					maxPosX,
					chartTop,
					maxPosX,
					chartBottom
			);

			// -----------------------------------------------------
			// Testo del delay
			//
			// sample -> secondi -> minuti
			// -----------------------------------------------------

			double delayMinutes =
					maxPair.delay / 60.0;

			String delayText;

			if (Math.abs(delayMinutes) < 0.05) {

				delayText = "0 min";

			} else {

				delayText =
						String.format(
								java.util.Locale.US,
								"%+.1f min",
								delayMinutes
						);
			}

			// -----------------------------------------------------
			// Posizione del testo
			//
			// Lo mettiamo vicino al punto massimo, ma evitiamo
			// di uscire dal grafico.
			// -----------------------------------------------------

			gi.setFont("Arial", 10);

			int textW =
					gi.getStringWidth(delayText);

			int labelX =
					maxPosX + 4;

			if (labelX + textW > chartX + chartWidth) {
				labelX =
						maxPosX - textW - 4;
			}

			int labelY;

			// Se il picco è nella parte superiore,
			// scriviamo sotto il punto.
			if (maxPosY < centerY) {
				labelY = maxPosY + 12;
			} else {
				labelY = maxPosY - 4;
			}

			// Evita di uscire verticalmente dal grafico.
			if (labelY < chartTop + 10) {
				labelY = chartTop + 10;
			}

			if (labelY > chartBottom - 2) {
				labelY = chartBottom - 2;
			}

			gi.drawString(
					delayText,
					labelX,
					labelY
			);

			// -----------------------------------------------------
			// Mostriamo anche R accanto al delay
			// -----------------------------------------------------

			String correlationText =
					String.format(
							java.util.Locale.US,
							"R=%.2f",
							maxPair.correlation
					);

			int corrW =
					gi.getStringWidth(correlationText);

			int corrX =
					maxPosX + 4;

			if (corrX + corrW > chartX + chartWidth) {
				corrX =
						maxPosX - corrW - 4;
			}

			int corrY = labelY + 11;

			if (corrY > chartBottom) {
				corrY = labelY - 11;
			}

			gi.drawString(
					correlationText,
					corrX,
					corrY
			);
		}

		// =========================================================
		// RIPRISTINO FONT
		// =========================================================

		gi.setFont("Arial", 16);
	}
}
