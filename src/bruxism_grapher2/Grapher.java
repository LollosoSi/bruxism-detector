package bruxism_grapher2;

import java.awt.AlphaComposite;
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

	int clenchline_height_low, clenchline_height_high;
	
	String file_name;

	ArrayList<Event> events;
	ArrayList<RawEvent> raw_events = null;

	BufferedImage android_icon;
	
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
		
		clenchline_height_low = legend_height - 25;
		clenchline_height_high = clenchline_height_low - 20;

		min_time = events.get(0).millis;
		max_time = events.get(events.size() - 1).millis;
		time_scale = (graph_width - 2 * side_margin) / (double) (max_time - min_time);
		xhour = time_scale * (6000 * 60);
		xcharsize = 9;
		
		
		try {
			android_icon = recolorPng(ImageIO.read(new File("icons/android.png")), Color.GREEN);
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
	
	public BufferedImage recolorPng(BufferedImage originalImage, Color tintColor) {
	    BufferedImage tintedImage = new BufferedImage(
	        originalImage.getWidth(),
	        originalImage.getHeight(),
	        BufferedImage.TYPE_INT_ARGB
	    );

	    Graphics2D g2d = tintedImage.createGraphics();

	    // Draw the original image's alpha (transparency)
	    g2d.drawImage(originalImage, 0, 0, null);

	    // Apply the tint color using SRC_ATOP to color only non-transparent pixels
	    g2d.setComposite(AlphaComposite.SrcAtop);
	    g2d.setColor(tintColor);
	    g2d.fillRect(0, 0, originalImage.getWidth(), originalImage.getHeight());

	    g2d.dispose();
	    return tintedImage;
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

	void drawRaw(Graphics2D g, boolean use_dark_mode) {
		
		if(raw_events == null)
			return;
		
		long syncmillis = -1;
		
		for(Event e : events) {
			if(e.type.equals("Sync")) {
				syncmillis = Long.valueOf(e.notes)-e.millis;
				break;
			}
		}
		
		if(syncmillis == -1) {
			System.out.println("Could not find the Sync tag, can't synchronize RAW data");
			return;
		}
		
		g.setColor(Colours.getColor(Color_element.Clenchline_guide, use_dark_mode));
		g.drawLine(xtimescale(min_time), clenchline_height_high, xtimescale(max_time), clenchline_height_high);
		g.drawLine(xtimescale(min_time), clenchline_height_low, xtimescale(max_time), clenchline_height_low);
		
		g.drawString("Undetected", xtimescale(min_time) - 9*10, clenchline_height_low+5);
		g.drawString("Detected", xtimescale(min_time) - 9*10, clenchline_height_high+5);
		
		g.setColor(Colours.getColor(Color_element.Clenchline, use_dark_mode));
		RawEvent last_event = null;
		for(RawEvent re : raw_events) {
			if(last_event == null) {
				last_event = re;
				continue;
			}
			
			boolean stop_drawing = (re.millis-syncmillis) > max_time;
			
			if((re.millis-syncmillis) > max_time)
				re.millis = max_time;
			
			if((re.millis-syncmillis) < min_time) {
				last_event = re;
				continue;
			}
			
			g.drawLine(xtimescale(last_event.millis-syncmillis), (last_event.value ? clenchline_height_high : clenchline_height_low), xtimescale(re.millis-syncmillis), (re.value ? clenchline_height_high : clenchline_height_low));
			
			if(stop_drawing)
				break;
			
			last_event = re;
		}
		
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
		
		drawRaw(g, use_dark_mode);

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

		boolean draw_android = false;
		int c = 0, cc = 1;
		long last_beep = 0, last_button = 0, last_alarm = 0, last_clench = 0, last_alarm_stop = 0;
		int countbeeps = 0;
		long lastbeepwrite = 0;
		Event le = null;
		for (Event e : events) {
			if(e.type.equals("ANDROID")) {
				draw_android = true;
			}
			
			if(countbeeps != 0 && !e.type.equals("Beep")) {
				
				if(le.millis-lastbeepwrite < findmsfromchars(2))
					cc++;
				else {
					cc=1;
					lastbeepwrite = le.millis;
				}
				
				g.setFont(new Font("Arial", Font.PLAIN, 14));
				g.setColor(Colours.getColor(Color_element.Warning, use_dark_mode));
				g.drawString(String.valueOf(countbeeps), xtimescale(le.millis), timeline_height + (14 * cc));
				
				g.setFont(new Font("Arial", Font.BOLD, 16));
				countbeeps = 0;
			}
			
			switch (e.type) {

			case "Beep":
				countbeeps++;
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
			
			le = e;
		}

		g.setColor(Colours.getColor(Color_element.Text, use_dark_mode));
		g.setFont(new Font("Arial", Font.PLAIN, 14));
		g.drawString("The data presented here has been corrected (-8s) for events that don't end with the alarm.",
				side_info_margin, graph_height - 32);
		g.drawString("Clenching events which lasted less than 1s are only drawn as red lines.", side_info_margin,
				graph_height - 16);

	
		g.drawImage(android_icon, graph_width-60, 20, 40, 40, null);
		
		
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

	public void addRawData(ArrayList<RawEvent> raw_events) {
		this.raw_events = raw_events;		
	}

}
