package bruxism_grapher2;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageIO;

import bruxism_grapher2.Colours.Color_element;

public class Grapher {

	StatData sd;

	int graph_width, graph_height;
	long min_time, max_time;
	double time_scale, xhour;

	int side_margin, side_info_margin;

	int timeline_height, legend_height, first_slot_height, info_text_height;
	int slot_spacing, slot_height;

	int tick_length, tick_slot_length, xcharsize;

	String file_name;

	ArrayList<Event> events;

	public Grapher(ArrayList<Event> event_list, String file_name) {
		events = event_list;
		this.file_name = file_name;

		graph_width = 1280;
		graph_height = 720;

		tick_length = 20;
		tick_slot_length = 20;

		side_margin = graph_width / 12;
		side_info_margin = graph_width / 20;
		info_text_height = 50;

		timeline_height = graph_height - 200;
		legend_height = graph_height - 80;
		first_slot_height = graph_height - 180;
		slot_height = 50;
		slot_spacing = 50;

		min_time = events.get(0).millis;
		max_time = events.get(events.size() - 1).millis;
		time_scale = (graph_width - 2 * side_margin) / (double) (max_time - min_time);
		xhour = time_scale * (6000 * 60);
		xcharsize = 9;

	}

	long findmsfromchars(int chars) {
		return (long) (((chars * 9)) / time_scale);
	}

	int xtimescale(long millis) {
		return side_margin + (int) (time_scale * millis);
	}

	void drawEventLine(Graphics2D g, long millis, String time, int slot_start, int slot_end, boolean text_right,
			Color cline, Color ctext) {
		// drawTimeTick(g, millis, time, slot_start, text_right, cline, ctext);
		g.setColor(cline);
		g.drawLine(xtimescale(millis), timeline_height, xtimescale(millis), getBaseYslot(slot_end) - slot_height);

	}

	void drawTimeTick(Graphics2D g, long millis, String time, int slot, boolean text_right, Color cline, Color ctext) {
		g.setColor(cline);
		g.drawLine(xtimescale(millis), timeline_height, xtimescale(millis),
				timeline_height + tick_length + (tick_slot_length * slot));
		g.setColor(ctext);
		g.drawString(time, xtimescale(millis) - (text_right ? -5 : (xcharsize * time.length())),
				timeline_height + tick_length + (tick_slot_length * slot));
	}

	void drawTimeBaseTick(Graphics2D g, long millis_start, String time_start, long millis_end) {
		int hour = Integer.valueOf(time_start.split(":")[0]);
		int minutes_to_hour = 60 - Integer.valueOf(time_start.split(":")[1]);
		int minutes_to_half = 30 - Integer.valueOf(time_start.split(":")[1]);

		long cur_millis = minutes_to_hour == 0 || minutes_to_hour == 60 ? millis_start
				: millis_start + (60000 * minutes_to_hour);
		long millis_half = minutes_to_half > 0 ? millis_start + (60000 * minutes_to_half) : cur_millis + (60000 * 30);
		do {
			hour++;
			g.drawLine(xtimescale(cur_millis), timeline_height, xtimescale(cur_millis),
					(int) (timeline_height + tick_length + (tick_slot_length * 1)));
			String time = String.format(" %02d:00", hour % 24);
			g.drawString(time, xtimescale(cur_millis) - (xcharsize * time.length()) / 2,
					timeline_height + tick_length + (tick_slot_length * 2));

			cur_millis += (60000 * 60);
		} while (cur_millis < millis_end);

		do {

			g.drawLine(xtimescale(millis_half), timeline_height, xtimescale(millis_half),
					(int) (timeline_height + tick_length + (tick_slot_length * 0.2)));

			millis_half += (60000 * 60);
		} while (millis_half < millis_end);
	}

	int getBaseYslot(int slot) {
		return first_slot_height - (slot * (slot_spacing + slot_height));
	}

	void drawDurationRectangle(Graphics2D g, long millis_start, long millis_stop, int slot, String text_top,
			Color cslot, Color ctext, Color fillcolor, int text_slot) {
		g.setColor(fillcolor);
		g.fillRect(xtimescale(millis_start), getBaseYslot(slot), xtimescale(millis_stop) - xtimescale(millis_start),
				slot_height);
		g.setColor(cslot);
		g.drawRect(xtimescale(millis_start), getBaseYslot(slot), xtimescale(millis_stop) - xtimescale(millis_start),
				slot_height);
		g.setColor(ctext);
		g.drawString(text_top, xtimescale(millis_stop) - (xcharsize * text_top.length()),
				getBaseYslot(slot) - slot_height - (16 * text_slot));
	}

	public BufferedImage generateGraph(boolean use_dark_mode) {
		// Create image
		BufferedImage img = new BufferedImage(graph_width, graph_height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();

		// Dark mode background
		g.setColor(Colours.getColor(Color_element.Background, use_dark_mode));
		g.fillRect(0, 0, graph_width, graph_height);

		g.setColor(Colours.getColor(Color_element.Text, use_dark_mode));
		g.setFont(new Font("Arial", Font.BOLD, 16));

		g.drawLine(xtimescale(min_time), timeline_height, xtimescale(max_time), timeline_height);

		drawTimeTick(g, events.get(0).millis, events.get(0).time, 0, false,
				Colours.getColor(Color_element.Text, use_dark_mode),
				Colours.getColor(Color_element.Text, use_dark_mode));

		drawTimeTick(g, events.get(events.size() - 1).millis, events.get(events.size() - 1).time, 2, true,
				Colours.getColor(Color_element.Text, use_dark_mode),
				Colours.getColor(Color_element.Text, use_dark_mode));

		drawTimeBaseTick(g, events.get(0).millis, events.get(0).time, events.get(events.size() - 1).millis);

		String session_name = events.get(0).notes.substring(events.get(0).notes.indexOf("Date: ") + 6,
				events.get(0).notes.length());
		// Session info table
		int proceedy = 20;
		int st = 0;
		sd = Statistics.calcStats(session_name, events);
		g.drawString("Date: " + session_name + " Filename: " + file_name, side_info_margin, info_text_height);
		g.drawString("Duration: " + (int) sd.duration + "h " + (int) ((sd.duration % 1) * 60) + "m", side_info_margin,
				info_text_height + (proceedy * ++st));
		g.drawString("Alarms: " + sd.alarm_count, side_info_margin, info_text_height + (proceedy * ++st));
		g.drawString("Warnings: " + sd.beep_count, side_info_margin, info_text_height + (proceedy * ++st));
		// g.drawString("Wake-ups: " + sd.button_count, side_info_margin,
		// info_text_height + (proceedy*++st));
		g.drawString("Clenching Events: " + sd.clench_count, side_info_margin, info_text_height + (proceedy * ++st));
		g.drawString("Clenching Rate: " + String.format("%.2f", sd.clenching_rate) + " per hour", side_info_margin,
				info_text_height + (proceedy * ++st));
		g.drawString("Stop After Beeps: " + sd.stop_after_beeps, side_info_margin,
				info_text_height + (proceedy * ++st));
		g.drawString("Not stopped After Beeps: " + sd.not_stop_after_beeps, side_info_margin,
				info_text_height + (proceedy * ++st));
		g.drawString("Avg beeps per event: " + sd.beeps_per_event, side_info_margin,
				info_text_height + (proceedy * ++st));
		g.drawString("Alarm percentage: " + sd.alarm_percentage + "%", side_info_margin,
				info_text_height + (proceedy * ++st));
		g.drawString("Average pauses: " + sd.average_time_between_clenching + "m", side_info_margin,
				info_text_height + (proceedy * ++st));
		g.drawString("Average clench duration: " + sd.average_clench_duration + "s", side_info_margin,
				info_text_height + (proceedy * ++st));

		int startx_legend = graph_width / 8;
		int y_legend = legend_height;
		int spacing = 300;

		g.setColor(Colours.getColor(Color_element.Button, use_dark_mode));
		g.fillRect(startx_legend + (spacing * 0), y_legend, 20, 20);
		g.drawString("Button", startx_legend + (spacing * 0) + 30, y_legend + 15);

		g.setColor(Colours.getColor(Color_element.Warning, use_dark_mode));
		g.fillRect(startx_legend + (spacing * 1), y_legend, 20, 20);
		g.drawString("Beep", startx_legend + (spacing * 1) + 30, y_legend + 15);

		g.setColor(Colours.getColor(Color_element.Clenching, use_dark_mode));
		g.fillRect(startx_legend + (spacing * 2), y_legend, 20, 20);
		g.drawString("Clenching", startx_legend + (spacing * 2) + 30, y_legend + 15);

		g.setColor(Colours.getColor(Color_element.Alarm, use_dark_mode));
		g.fillRect(startx_legend + (spacing * 3), y_legend, 20, 20);
		g.drawString("Alarm", startx_legend + (spacing * 3) + 30, y_legend + 15);

		int c = 0;
		long last_beep = 0, last_button = 0, last_alarm = 0, last_clench = 0, last_alarm_stop = 0;
		for (Event e : events) {
			switch (e.type) {

			case "Beep":

				drawEventLine(g, e.millis, (e.millis - last_beep > findmsfromchars(5) ? e.time : ""), 0, 0, false,
						Colours.getColor(Color_element.Warning, use_dark_mode),
						Colours.getColor(Color_element.Text, use_dark_mode));

				last_beep = e.millis;

				break;

			case "Alarm":
				if (e.notes.equals("STARTED")) {
					drawEventLine(g, e.millis, e.time, 4, 3, false,
							Colours.getColor(Color_element.Alarm, use_dark_mode),
							Colours.getColor(Color_element.Text, use_dark_mode));
					last_alarm = e.millis;
				} else {
					last_alarm_stop = e.millis;
				}
				break;

			case "Button":
				drawEventLine(g, e.millis, "", 3, 2, false, Colours.getColor(Color_element.Button, use_dark_mode),
						Colours.getColor(Color_element.Text, use_dark_mode));
				last_button = e.millis;
				break;

			case "Clenching":

				if (e.notes.equals("STARTED")) {
					if (e.millis - last_clench > 60000 * 10)
						c = 0;
					last_clench = e.millis;
					drawEventLine(g, e.millis, "", 2, 1, false,
							Colours.getColor(Color_element.Clenching, use_dark_mode),
							Colours.getColor(Color_element.Text, use_dark_mode));
				} else {

					double duration = (double) (e.duration - ((last_alarm_stop == e.millis) ? 0 : 8));
					duration = (int) (duration * 10.0) / 10.0;
					// double duration = e.duration;
					String d = duration + "s";

					drawDurationRectangle(g, last_clench, e.millis, 1, (duration < 1) ? "" : d,
							Colours.getColor(Color_element.Clenching, use_dark_mode),
							Colours.getColor(Color_element.Text, use_dark_mode), Colours.getColor(Color_element.Clenchfill, use_dark_mode), (duration < 1) ? 0 : c++ % 25);
				}
				break;
			default:
				continue;
			}
		}

		g.setColor(Colours.getColor(Color_element.Text, use_dark_mode));
		g.setFont(new Font("Arial", Font.PLAIN, 14));
		g.drawString("The data presented here has been corrected (-8s) for events that don't end with the alarm.",
				side_info_margin, graph_height - 32);
		g.drawString("Clenching events which lasted less than 1s are only drawn as red lines.", side_info_margin,
				graph_height - 16);

		return img;
	}
	
	public StatData getStats(){return sd;}

	public static boolean writeImage(BufferedImage img, String file_name) {
		try {
			ImageIO.write(img, "png", new File(file_name.replace(".csv", ".png")));
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

}
