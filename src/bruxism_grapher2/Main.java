package bruxism_grapher2;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;


public class Main {

	public static void main(String[] args) {
		File dir = new File(".");
		File[] files = null;

		boolean dark_theme = true;
		
		if (args.length != 0) {
			ArrayList<String> argss = new ArrayList<String>();
			
			for(String s : args) {
				if(s.toLowerCase().equals("light")) {
					dark_theme = false;
					continue;
				}
				if(s.toLowerCase().equals("dark")) {
					dark_theme = true;
					continue;
				}
				argss.add(s);
			}

			files = argss.stream().map(File::new).toArray(File[]::new);
		}
		
		if(files == null || files.length == 0) {
			files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".csv"));
			if (files == null || files.length == 0) {
				System.out.println("No CSV files found in the directory.");
				return;
			}
		}

		ArrayList<StatData> sda = new ArrayList<StatData>();
		File f = new File("./Graphs/");
		f.mkdirs();
		
		for (File file : files) {
			
			ArrayList<Event> events = FileEventReader.readCSV(file.getName());
			if (!events.isEmpty()) {
				Grapher gg = new Grapher(events, file.getName());
				
				
				Grapher.writeImage(gg.generateGraph(dark_theme), "./Graphs/"+file.getName());
				sda.add(gg.getStats());
			}
		}
		
		sda.sort( (a, b) -> { return a.compareTo(b); } );
		
		File ff = new File("./Summary/");
		ff.mkdirs();
		
		try {
			PrintWriter pw = new PrintWriter("./Summary/summary.csv");
			pw.println(StatData.produce_csv_header());
			for(StatData sd : sda) {
				pw.println(sd.produce_csv_line());
			}
			pw.close();
			
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		
	}

}
