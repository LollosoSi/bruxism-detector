package bruxism_grapher2;

import java.awt.Desktop;
import java.awt.GridLayout;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.Border;


public class Main {
	
	static JLabel messages;
	
	static boolean dark_theme = true;
	
	public static void main(String[] args) {
		
        JFrame frame = new JFrame("Bruxism Grapher");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400,400);
        frame.setLocationRelativeTo(null); // Center on screen

        messages = new JLabel("Select an option");
        messages.setHorizontalAlignment(SwingConstants.CENTER);
        JButton receiveButton = new JButton("Receive from Android");
        JButton graphButton = new JButton("Create Graphs");
        
        ToggleSwitch ts = new ToggleSwitch();
        JLabel switchstate = new JLabel();
        

        ts.setActivated(dark_theme);
		switchstate.setText(ts.isActivated() ? "Dark mode" : "Light mode");

        ts.addMouseListener(new MouseListener() {
			
			@Override
			public void mouseReleased(MouseEvent e) {
				switchstate.setText(ts.isActivated() ? "Dark mode" : "Light mode");
				dark_theme = ts.isActivated();
			}
			
			@Override
			public void mousePressed(MouseEvent e) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void mouseExited(MouseEvent e) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void mouseEntered(MouseEvent e) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void mouseClicked(MouseEvent e) {
				// TODO Auto-generated method stub
				
			}
		});
        

        receiveButton.addActionListener(e -> {
            new Thread(() -> {
                messages.setText("Server is running.\nWaiting for Android...");
                FileReceiverServer fsr = new FileReceiverServer();
        		fsr.main(args);
        		messages.setText("Creating graphs");
        		createGraphs(args);
        		openGraphFolder();
        		System.exit(0);
            }).start();
        });

        graphButton.addActionListener(e -> {
            // Placeholder: Add your graph logic here
        	messages.setText("Creating graphs");
            createGraphs(args);
            openGraphFolder();
            System.exit(0);
        });

        JPanel jp = new JPanel(new GridLayout(1,2,50,50));
        jp.add(ts);
        jp.add(switchstate);
        
        JPanel panel = new JPanel(new GridLayout(4, 1, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.add(messages);
        panel.add(jp);
        panel.add(receiveButton);
        panel.add(graphButton);

        frame.setContentPane(panel);
        frame.setVisible(true);
		
		
		
		
		
		
		
	}
	
	public static void createGraphs(String [] args) {
		File dir = new File(".");
		File[] files = null;

		
		
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
				
				File rawfile = new File("RAW/"+file.getName().replace(".csv","_RAW.csv"));
				if(rawfile.exists()) {
					gg.addRawData(FileRawEventReader.readCSV(rawfile.getAbsolutePath()));
				} else {
					System.out.println(rawfile.getPath() + " was not found, consider including your raw files.");
				}
				
				
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
	
	static void openGraphFolder() {
		
		try {
		    String graphFolderPath = "Graphs";
		    File graphFolder = new File(graphFolderPath);

		    if (!graphFolder.exists()) {
		    	messages.setText("Graph folder does not exist.");
		    } else {
		        Desktop.getDesktop().open(graphFolder);
		    }
		} catch (Exception ex) {
		    ex.printStackTrace();
		    messages.setText("Failed to open graph folder: " + ex.getMessage());
		}
		
	}

}
