package com.example.bruxismdetector.bruxism_grapher2;

import com.example.bruxismdetector.bruxism_grapher2.grapher_interfaces.GrapherInterface;
import com.example.bruxismdetector.bruxism_grapher2.grapher_interfaces.IconManager;
import com.example.bruxismdetector.bruxism_grapher2.Colours.Color_element;
import java.util.ArrayList;

public class Grapher<Image, Color, Font> {

	public void setPlatformSpecificAbstractions(GrapherInterface<Color, Image, Font> g, IconManager<Color, Image> im) {
		gi = g;
		icm = im;

		calculateGraphParameters();
	}

	StatData sd = null;

	int graph_width, graph_height;
	long min_time, max_time;
	double time_scale, xhour;

	int side_margin, side_info_margin;

	int timeline_height, legend_height, first_slot_height, info_text_height;
	int slot_spacing, slot_height;

	int tick_length, tick_slot_length, xcharsize;

	int clenchline_height_low, clenchline_height_high;

	String file_name;

	ArrayList<Event> events;
	ArrayList<RawEvent> raw_events = null;

	GrapherInterface<Color, Image, Font> gi = null;
	IconManager<Color, Image> icm = null;
	Image android_icon, medicationIcon, stressedIcon, alcoholIcon, badMealIcon, dayPainIcon, workoutIcon, hydratedIcon, coffeeIcon, lifeEventIcon, anxietyIcon, botoxIcon, sickIcon, badMoodIcon, goodMoodIcon, onlyAlarmsIcon;

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

		timeline_height = graph_height - 240;
		legend_height = graph_height - 80;
		first_slot_height = timeline_height;
		slot_height = 30;
		slot_spacing = 20;

		clenchline_height_low = legend_height - 25;
		clenchline_height_high = clenchline_height_low - 60;

		min_time = events.get(0).millis;
		max_time = events.get(events.size() - 1).millis;
		time_scale = (graph_width - 2 * side_margin) / (double) (max_time - min_time);
		xhour = time_scale * (6000 * 60);
		xcharsize = 9;

		loadIcons();

	}



	public void loadIcons() {

		android_icon = icm.loadImage("android.png", "#00Fc00");
		medicationIcon = icm.loadImage("medication.png", "#Fc0000");
		stressedIcon = icm.loadImage("stressed.png", "#F44336");
		alcoholIcon = icm.loadImage("alcohol.png", "#FFEB3B");
		badMealIcon = icm.loadImage("bad_meal.png", "#FF9800");
		dayPainIcon = icm.loadImage("day_pain.png", "#F44336");
		workoutIcon = icm.loadImage("workout.png","#4CAF50");
		hydratedIcon = icm.loadImage("hydrated.png", "#2196F3");
		coffeeIcon = icm.loadImage("coffee.png", "#FF9800");
		lifeEventIcon = icm.loadImage("life_event.png", "#FF9800");
		anxietyIcon = icm.loadImage("anxiety.png", "#FFEB3B");
		sickIcon = icm.loadImage("sick.png", "#FF9800");
		badMoodIcon = icm.loadImage("bad.png", "#F44336");
		goodMoodIcon = icm.loadImage("good.png", "#00BCD4");
		botoxIcon = icm.loadImage("botox.png", "#4CAF50");
		onlyAlarmsIcon = icm.loadImage("onlyalarms.png","#4CAF50");


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
		gi.drawLine(xtimescale(millis), timeline_height, xtimescale(millis), getBaseYslot(slot_end) - slot_height);

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
				getBaseYslot(slot) - slot_height - (16 * text_slot));
	}


	public float calculatePercentage(int value, int maxValue, int minValue) {
		// Calculate percentage of value from the baseline relative to minAverage
		return (float) (value - minValue) / (maxValue - minValue);
	}
	public int calculateHeightFromPercentage(float percentage, int clenchlineHeightLow, int clenchlineHeightHigh) {
		// Map percentage to height between the low and high values
		return (int) (clenchlineHeightLow + ((clenchlineHeightHigh - clenchlineHeightLow) * percentage));
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
				gi.drawImage(android_icon, graph_width - 60, 30, 40, 40);
			}

			if (e.type.equals("INFO")) {
				Image icon = null;

				switch (e.notes.toLowerCase()) {
					case "workout":
						icon = workoutIcon;
						break;
					case "hydrated":
						icon = hydratedIcon;
						break;
					case "stressed":
						icon = stressedIcon;
						break;
					case "caffeine":
						icon = coffeeIcon;
						break;
					case "anxious":
						icon = anxietyIcon; // If you want a separate icon, replace with anxiousIcon
						break;
					case "alcohol":
						icon = alcoholIcon;
						break;
					case "latedinner":
						icon = badMealIcon; // Assuming you use the same for bad meal
						break;
					case "medications":
						icon = medicationIcon;
						break;
					case "pain":
						icon = dayPainIcon;
						break;
					case "lifeevent":
						icon = lifeEventIcon;
						break;
					case "botox":
						icon = botoxIcon;
						break;
				}


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
				Image icon = null;

				switch(e.notes.toLowerCase()) {
					case "onlyalarm":
						icon = onlyAlarmsIcon;
						break;
				}

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
				Image moodIcon = null;

				switch (e.notes) {
					case "Good":
						moodIcon = goodMoodIcon;
						break;
					case "Bad":
						moodIcon = badMoodIcon;
						break;
					case "Sick":
						moodIcon = sickIcon;
						break;
				}

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

	public Image generateGraph(boolean use_dark_mode) {

		if(gi==null)
			throw new NullPointerException("You did not call platformSpecificAbstractions() before generating the graph");

		// Dark mode background
		gi.setColor(gi.convertColor(Colours.getColor(Color_element.Background, use_dark_mode)));
		gi.fillRect(0, 0, graph_width, graph_height);

		gi.setColor(gi.convertColor(Colours.getColor(Color_element.Text, use_dark_mode)));
		gi.setFont("Arial", 16);

		gi.drawLine(xtimescale(min_time), timeline_height, xtimescale(max_time), timeline_height);

		drawRaw(use_dark_mode);

		drawTimeTick(events.get(0).millis, events.get(0).time, 0, false,
				gi.convertColor(Colours.getColor(Color_element.Text, use_dark_mode)),
				gi.convertColor(Colours.getColor(Color_element.Text, use_dark_mode)));

		drawTimeTick(events.get(events.size() - 1).millis, events.get(events.size() - 1).time, 2, true,
				gi.convertColor(Colours.getColor(Color_element.Text, use_dark_mode)),
				gi.convertColor(Colours.getColor(Color_element.Text, use_dark_mode)));

		drawTimeBaseTick(events.get(0).millis, events.get(0).time, events.get(events.size() - 1).millis);

		String session_name = events.get(0).notes.substring(events.get(0).notes.indexOf("Date: ") + 6,
				events.get(0).notes.length());
		// Session info table
		int proceedy = 20;
		int st = 0;
		sd = getStats();
		gi.drawString("Date: " + session_name + " Filename: " + file_name, side_info_margin, info_text_height);
		gi.drawString("Duration: " + (int) sd.duration + "h " + (int) ((sd.duration % 1) * 60) + "m", side_info_margin,
				info_text_height + (proceedy * ++st));
		gi.drawString("Alarms: " + sd.alarm_count, side_info_margin, info_text_height + (proceedy * ++st));
		gi.drawString("Warnings: " + sd.beep_count, side_info_margin, info_text_height + (proceedy * ++st));
		// gi.drawString("Wake-ups: " + sd.button_count, side_info_margin,
		// info_text_height + (proceedy*++st));
		gi.drawString("Clenching Events: " + sd.clench_count, side_info_margin, info_text_height + (proceedy * ++st));
		gi.drawString("Clenching Rate: " + String.format("%.2f", sd.clenching_rate) + " per hour", side_info_margin,
				info_text_height + (proceedy * ++st));
		gi.drawString("Stop After Beeps: " + sd.stop_after_beeps, side_info_margin,
				info_text_height + (proceedy * ++st));
		gi.drawString("Not stopped After Beeps: " + sd.not_stop_after_beeps, side_info_margin,
				info_text_height + (proceedy * ++st));
		gi.drawString("Avg beeps per event: " + sd.beeps_per_event, side_info_margin,
				info_text_height + (proceedy * ++st));
		gi.drawString("Alarm percentage: " + sd.alarm_percentage + "%", side_info_margin,
				info_text_height + (proceedy * ++st));
		gi.drawString("Average pauses: " + sd.average_time_between_clenching + "m", side_info_margin,
				info_text_height + (proceedy * ++st));
		gi.drawString("Average clench duration: " + sd.average_clench_duration + "s", side_info_margin,
				info_text_height + (proceedy * ++st));
		gi.drawString("Total clenching time: " + sd.total_clench_duration + "s", side_info_margin,
				info_text_height + (proceedy * ++st));
		gi.drawString("Active time: " + sd.active_time_percentage + "‰", side_info_margin,
				info_text_height + (proceedy * ++st));

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
					drawEventLine(e.millis, (e.millis - last_beep > findmsfromchars(5) ? e.time : ""), 0, 0, false,
							gi.convertColor(Colours.getColor(Color_element.Warning, use_dark_mode)),
							gi.convertColor(Colours.getColor(Color_element.Text, use_dark_mode)));

					last_beep = e.millis;

					break;

				case "Alarm":
					if (e.notes.equals("STARTED")) {
						drawEventLine(e.millis, e.time, 4, 3, false,
								gi.convertColor(Colours.getColor(Color_element.Alarm, use_dark_mode)),
								gi.convertColor(Colours.getColor(Color_element.Text, use_dark_mode)));
						last_alarm = e.millis;
					} else {
						last_alarm_stop = e.millis;
					}
					break;

				case "Button":
					drawEventLine(e.millis, "", 3, 2, false, gi.convertColor(Colours.getColor(Color_element.Button, use_dark_mode)),
							gi.convertColor(Colours.getColor(Color_element.Text, use_dark_mode)));
					last_button = e.millis;
					break;

				case "Clenching":

					if (e.notes.equals("STARTED")) {
						if (e.millis - last_clench > 60000 * 10)
							c = 0;
						last_clench = e.millis;
						drawEventLine(e.millis, "", 2, 1, false,
								gi.convertColor(Colours.getColor(Color_element.Clenching, use_dark_mode)),
								gi.convertColor(Colours.getColor(Color_element.Text, use_dark_mode)));
					} else {

						double duration = (double) (e.duration - ((last_alarm_stop == e.millis) ? 0 : 8));
						duration = (int) (duration * 10.0) / 10.0;
						// double duration = e.duration;
						String d = duration + "s";

						drawDurationRectangle(last_clench, e.millis, 1, (duration < 1) ? "" : d,
								gi.convertColor(Colours.getColor(Color_element.Clenching, use_dark_mode)),
								gi.convertColor(Colours.getColor(Color_element.Text, use_dark_mode)),
								gi.convertColor(Colours.getColor(Color_element.Clenchfill, use_dark_mode)), (duration < 1) ? 0 : c++ % 25);
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
			String session_name = events.get(0).notes.substring(events.get(0).notes.indexOf("Date: ") + 6,
					events.get(0).notes.length());
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
