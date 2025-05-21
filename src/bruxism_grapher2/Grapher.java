package bruxism_grapher2;

import grapher_interfaces.GrapherInterface;
import grapher_interfaces.IconManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import bruxism_grapher2.Colours.Color_element;

import java.util.Arrays;
import java.util.Calendar;
public class Grapher<Image, Color, Font> {

	public void setPlatformSpecificAbstractions(GrapherInterface<Color, Image, Font> g, IconManager<Color, Image> im) {
		gi = g;
		icm = im;

		calculateGraphParameters();
	}


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

	static final int
			alarm_slot = 0, alarm_slot_length = 3,
			clenching_slot = 4, clenching_slot_length = 1,
			button_slot = 1, button_slot_length = 2,
			beep_slot = 0, beep_slot_length = 1;

	String file_name;

	ArrayList<Event> events;
	ArrayList<RawEvent> raw_events = null;

	GrapherInterface<Color, Image, Font> gi = null;
	IconManager<Color, Image> icm = null;
	Map<String, Image> icons = new HashMap<String, Image>();

	public Grapher(ArrayList<Event> event_list, String file_name, int width, int height) {
		events = event_list;
		this.file_name = file_name;

		graph_width = width;
		graph_height = height;
	}

	void calculateGraphParameters() {

		tick_length = 20;
		tick_slot_length = 20;

		side_margin = graph_width / 12;
		side_info_margin = graph_width / 20;
		info_text_height = 50;

		legend_height = graph_height - 80;

		int line_height_temp = legend_height;

		if(raw_events != null) {
			if(!raw_events.isEmpty()) {
				clenchline_height_low = (line_height_temp -= 25);
				clenchline_height_high = (line_height_temp -= 30);
			}
		}

		if(!sleepData.heartrate.isEmpty()) {
			heartrate_height_low = (line_height_temp -= 25);
			heartrate_height_high = (line_height_temp -= 30);
		}

		if(!sleepData.spo2.isEmpty() || !sleepData.stress.isEmpty()) {
			spo2_height_low = (line_height_temp -= 25);
			spo2_height_high = (line_height_temp -= 30);
		}

		//if(!sleepData.stress.isEmpty()) {
		//	stress_height_low = (line_height_temp -= 25);
		//	stress_height_high = (line_height_temp -= 30);
		//}

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


		icons.put("android", icm.loadImage("android.png", Nice));
		icons.put("medication", icm.loadImage("medication.png", Bad));
		icons.put("stressed", icm.loadImage("stressed.png", Bad));
		icons.put("alcohol", icm.loadImage("alcohol.png", Mediocre));
		icons.put("skipped or late dinner", icm.loadImage("bad_meal.png", Mediocre));
		icons.put("pain", icm.loadImage("day_pain.png", Bad));
		icons.put("workout", icm.loadImage("workout.png", Nice));
		icons.put("hydrated", icm.loadImage("hydrated.png", Neutral));
		icons.put("caffeine", icm.loadImage("coffee.png", Mediocre));
		icons.put("life event", icm.loadImage("life_event.png", Mediocre));
		icons.put("anxious", icm.loadImage("anxiety.png", Mediocre));
		icons.put("sick", icm.loadImage("sick.png", Mediocre));
		icons.put("bad", icm.loadImage("bad.png", Bad));
		icons.put("good", icm.loadImage("good.png", Nice));
		icons.put("botox", icm.loadImage("botox.png", Nice));
		icons.put("onlyalarm", icm.loadImage("onlyalarms.png", Neutral));
		icons.put("tired", icm.loadImage("tired.png", Mediocre));
		icons.put("mouth guard", icm.loadImage("mouthguard.png", Neutral));

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

		SleepData.SleepStage previous = null;
		for(SleepData.SleepStage ss : data) {
			if(drawSleepStageOutline(previous, ss, unixcorrection))
				if(previous==null)
					continue;
				else
					break;

			previous = ss;
		}

	}

	void drawSleepRecords(String name, ArrayList<SleepData.Record> data, int height_high, int height_low, boolean use_dark_mode, Color_element linecolor, int standard_minval, int standard_maxval, boolean drawRight) {

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

			gi.setColor(gi.convertColor(Colours.getColor(linecolor, use_dark_mode)));
			gi.drawString(name, xtimescale(min_time) - 9 * 10, height_low - ((height_low-height_high)/2) + 7);

		}else {
			gi.drawString(String.valueOf(minFvalue), xtimescale(max_time) + 9, height_low + 7);
			gi.drawString(String.valueOf(avgFvalue), xtimescale(max_time) + 9, height_low - ((height_low-height_high)/2) + 7);
			gi.drawString(String.valueOf(maxFvalue), xtimescale(max_time) + 9, height_high + 7);

			gi.setColor(gi.convertColor(Colours.getColor(linecolor, use_dark_mode)));
			gi.drawString(name, xtimescale(max_time) + (9*4), height_low - ((height_low-height_high)/2) + 7);

		}


		if(standard_maxval>0)
			maxFvalue = standard_maxval;
		if(standard_minval>0)
			minFvalue = standard_minval;

		gi.setColor(gi.convertColor(Colours.getColor(linecolor, use_dark_mode)));
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

			gi.drawLine(xtimescale(((last_event.unix_sec+unixcorrection)*1000)),
					calculateHeightFromPercentage(calculatePercentage(last_event.value, maxFvalue, minFvalue), height_low, height_high),
					xtimescale(((re.unix_sec+unixcorrection)*1000)),
					calculateHeightFromPercentage(calculatePercentage(re.value, maxFvalue, minFvalue), height_low, height_high));

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
		int iconSize = 32;
		int spacing = 10;
		int starty = graphY;
		int x = graphX;
		int y = graphY;
		int count = 0;

		for (Event e : events) {
			if (e.type.equals("ANDROID")) {
				gi.drawImage(icons.get("android"), graph_width - 60, 30, 40, 40);
			}

			if (e.type.toLowerCase().equals("info")) {
				Image icon = icons.get(e.notes.toLowerCase());

				if (icon != null) {
					if (count++ % 2 == 0) {
						x -= iconSize + spacing;
						y = starty;
					}
					gi.drawImage(icon, x, y, iconSize, iconSize);
					y += iconSize + spacing;
				}
			}

			if (e.type.equals("SESSION")) {
				Image icon = icons.get(e.notes.toLowerCase());

				if (icon != null) {
					if (count++ % 2 == 0) {
						x -= iconSize + spacing;
						y = starty;
					}
					gi.drawImage(icon, x, y, iconSize, iconSize);
					y += iconSize + spacing;
				}
			}

			if (e.type.equals("MOOD")) {
				Image moodIcon = icons.get(e.notes.toLowerCase());

				if (moodIcon != null) {
					if (count++ % 2 == 0) {
						x -= iconSize + spacing;
						y = starty;
					}
					gi.drawImage(moodIcon, x, y, iconSize, iconSize);
					y += iconSize + spacing;
				}
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
			sum+=maxchars_row[i-1]*10;
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
		String session_name = startnote[startnote.length-1]; // It's a string date YYYY-MM-DD
		return session_name;
	}
	
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

		drawRaw(use_dark_mode);
		drawSleepRecords("BPM", sleepData.heartrate,heartrate_height_high, heartrate_height_low, use_dark_mode, Color_element.Hrline, 30, -1,false);
		drawSleepRecords("SpO2", sleepData.spo2,spo2_height_high, spo2_height_low, use_dark_mode, Color_element.Spoline, 0, 100,false);
		drawSleepRecords("Stress", sleepData.stress,spo2_height_high, spo2_height_low, use_dark_mode, Color_element.Stressline, 0, 100,true);

		drawTimeTick(events.get(0).millis, events.get(0).time, 0, false,
				gi.convertColor(Colours.getColor(Color_element.Text, use_dark_mode)),
				gi.convertColor(Colours.getColor(Color_element.Text, use_dark_mode)));

		drawTimeTick(events.get(events.size() - 1).millis, events.get(events.size() - 1).time, 2, true,
				gi.convertColor(Colours.getColor(Color_element.Text, use_dark_mode)),
				gi.convertColor(Colours.getColor(Color_element.Text, use_dark_mode)));

		drawTimeBaseTick(events.get(0).millis, events.get(0).time, events.get(events.size() - 1).millis);


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


		if(!sleepData.sleep_stages.isEmpty()) {

			int p1 = (int)(100.0f*calculatePercentage(sleepData.duration_lightsleep, sleepData.duration_sleep, 0)), p2 = (int)(100.0f*calculatePercentage(sleepData.duration_deepsleep, sleepData.duration_sleep, 0)), p3 = (int)(100.0f*calculatePercentage(sleepData.duration_rem, sleepData.duration_sleep, 0));

			infostats.add("Sleep duration: " + sleepData.duration_sleep/60 + "h "+ sleepData.duration_sleep%60 +"m");
			infostats.add("Light sleep: " + sleepData.duration_lightsleep/60 + "h "+ sleepData.duration_lightsleep%60 +"m (" + (p1) +"%)");
			infostats.add("Deep sleep: " + sleepData.duration_deepsleep/60 + "h "+ sleepData.duration_deepsleep%60 +"m (" + (p2) +"%)");
			infostats.add("REM: " + sleepData.duration_rem/60 + "h "+ sleepData.duration_rem%60 +"m (" + (p3) +"%)");
			infostats.add("Awake: " + sleepData.duration_awake/60 + "h "+ sleepData.duration_awake%60 +"m ("+sleepData.awake_count+")");

			infostats.add("Average BPM: " + sleepData.average_hr);
			infostats.add("Breath Quality: " + sleepData.average_breath_quality + "%" );

			drawSleepStages(sleepData.sleep_stages);

		}

		drawInfoStats(infostats, 7, use_dark_mode);

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
						//drawEventLine(e.millis, "", 2, 1, false,
						//		gi.convertColor(Colours.getColor(Color_element.Clenching, use_dark_mode)),
						//		gi.convertColor(Colours.getColor(Color_element.Text, use_dark_mode)));
					} else {

						double duration = (double) (e.duration - ((last_alarm_stop == e.millis) ? 0 : 4));
						duration = (int) (duration * 10.0) / 10.0;
						// double duration = e.duration;
						String d = duration + "s";

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
		gi.drawString("The data presented here has been corrected (-8s) for events that don't end with the alarm.",
				side_info_margin, graph_height - 32);
		gi.drawString("Clenching events which lasted less than 1s are only drawn as red lines.", side_info_margin,
				graph_height - 16);

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

}

