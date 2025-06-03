package com.example.bruxismdetector.bruxism_grapher2;

import com.example.bruxismdetector.bruxism_grapher2.grapher_interfaces.GrapherInterface;
import com.example.bruxismdetector.bruxism_grapher2.grapher_interfaces.IconManager;
import com.example.bruxismdetector.bruxism_grapher2.grapher_interfaces.TaskRunner;

import java.util.ArrayList;
import java.util.List;

public class HeatMapGraph<Image, Color, Font> {

    GrapherInterface<Color, Image, Font> gi;
    IconManager<Color, Image> icm;
    TaskRunner taskRunner;
    CorrelationsCalculator correlationsCalculator;

    List<String> tags;
    List<String> stats;
    float[][] correlations; // [tag][stat]
    String[] evaluations;   // "positive", "neutral", "negative"

    public int graph_width = 1280, graph_height = 1280;

    int cellWidth = 100;
    int cellHeight = 50;
    int labelWidth = 100;
    int headerHeight = 100;

    public void setPlatformSpecificAbstractions(GrapherInterface<Color, Image, Font> g, IconManager<Color, Image> im, TaskRunner tr) {
        gi = g;
        icm = im;
        taskRunner = tr;
        correlationsCalculator=new CorrelationsCalculator(gi.getRecordingsPath().getPath()+"/Summary/Summary.csv");
        calculateGraphParameters();
    }

    void calculateGraphParameters() {

    }

    public Image generateGraphCorrelations(){
        return generateGraph(correlationsCalculator.makeCorrelationMatrix(), correlationsCalculator.getFilterNames(), correlationsCalculator.getStatNames());
    }

    private Color getColorFromValue(float value) {
        // Normalize to range -1 to 1
        float clamped = Math.max(-1f, Math.min(1f, value));
        int r = (int) (255 * (clamped > 0 ? clamped : 0));
        int b = (int) (255 * (clamped < 0 ? -clamped : 0));
        return gi.convertColor(String.format("#%02x%02x%02x", r, 0, b));
    }

    public Image generateGraph(double[][] data, ArrayList<String> tag_names, ArrayList<String> data_names) {
        final double threshold = 0.2;
        final String[] evals = new String[]{"Negative", "Mostly negative", "Neutral", "Mostly positive", "Positive"};

        int rows = tag_names.size();
        int cols = data_names.size();

        // Dynamic sizing based on available image size
        int maxCellSizeX = (graph_width * 3 / 5) / cols;
        int maxCellSizeY = (graph_height * 3 / 5) / rows;
        int cellSize = Math.min(maxCellSizeX, maxCellSizeY);

        int fontSize = Math.max(12, cellSize / 2);
        int rowLabelWidth = cellSize * 2;
        int evalLabelWidth = cellSize * 2;
        int topLabelHeight = cellSize * 3;

        int heatmapWidth = cols * cellSize;
        int heatmapHeight = rows * cellSize;
        int totalWidth = rowLabelWidth + heatmapWidth + evalLabelWidth;
        int totalHeight = topLabelHeight + heatmapHeight;

        int offsetX = (graph_width - totalWidth) / 2;
        int offsetY = (graph_height - totalHeight) / 2;

        gi.setImageSize(graph_width, graph_height);
        gi.setColor(gi.convertColor("#FFFFFF"));
        gi.fillRect(0, 0, graph_width, graph_height);

        gi.setFont("Arial", fontSize);

        // Draw top column labels (metric names) as rotated multi-line
        int wordsPerLineTop = 3;

        for (int c = 0; c < cols; c++) {
            List<String> lines = splitEveryNWords(data_names.get(c), wordsPerLineTop);

            int baseX = offsetX + rowLabelWidth + c * cellSize + (cellSize / 2);
            int tHeight = fontSize;
            int baseY = offsetY + topLabelHeight - (tHeight / 4); // center vertically in label area

            for (int i = 0; i < lines.size(); i++) {
                int x = baseX + i * fontSize/2;
                gi.setColor(gi.convertColor("#000000"));
                gi.drawRotatedString(lines.get(i), x, baseY, -90); // centered X, stacked Y
            }
        }

        // Draw heatmap grid and evaluation labels
        for (int r = 0; r < rows; r++) {
            int y = offsetY + topLabelHeight + r * cellSize;
            int posnegcount = 0;

            for (int c = 0; c < cols; c++) {
                int x = offsetX + rowLabelWidth + c * cellSize;
                double val = data[r][c];

                byte effect = CorrelationsCalculator.isGoingToBetter(val, data_names.get(c));
                if (Math.abs(val) < threshold) {
                    effect = CorrelationsCalculator.NeutralCorr;
                } else {
                    posnegcount += effect == CorrelationsCalculator.PositiveCorr ? 1
                            : (effect == CorrelationsCalculator.NegativeCorr ? -1 : 0);
                }

                // Determine color
                String color = "#CCCCCC";
                if (effect == CorrelationsCalculator.PositiveCorr) color = "#88FF88";
                else if (effect == CorrelationsCalculator.NegativeCorr) color = "#FF8888";
                else if (effect == CorrelationsCalculator.NeutralCorr) color = "#AAAAFF";

                gi.setColor(gi.convertColor(color));
                gi.fillRect(x, y, cellSize, cellSize);

                gi.setColor(gi.convertColor("#000000"));
                gi.drawRect(x, y, cellSize, cellSize);
            }

            // Overall evaluation
            int ab = Math.abs(posnegcount);
            boolean positive = posnegcount > 0;
            int selected = ab < 2 ? 2 : ab < 3 ? (positive ? 3 : 1) : (positive ? 4 : 0);
            String evalLabel = evals[selected];
            String evalColor = selected == 4 ? "#22AA22" : selected == 3 ? "#66CC66"
                    : selected == 2 ? "#4444CC" : selected == 1 ? "#CC6666" : "#AA2222";

            int evalX = offsetX + rowLabelWidth + cols * cellSize + 10;
            gi.setColor(gi.convertColor(evalColor));
            gi.drawString(evalLabel, evalX, y + cellSize / 2);
        }

        // Draw left row labels last (to stay on top)
        int wordsPerLineSide = 2;

        gi.setFont("Arial", fontSize);
        for (int r = 0; r < rows; r++) {
            List<String> lines = splitEveryNWords(tag_names.get(r), wordsPerLineSide);
            int tHeight = lines.size() * fontSize;
            int baseX = offsetX + 5;
            int baseY = offsetY + topLabelHeight + r * cellSize + (cellSize / 2) ;

            for (int i = 0; i < lines.size(); i++) {
                int y = baseY + i * fontSize/2;
                gi.setColor(gi.convertColor("#000000"));
                gi.drawString(lines.get(i), baseX, y);
            }
        }




        // Draw full grid lines
        gi.setColor(gi.convertColor("#000000"));
        for (int c = 0; c <= cols; c++) {
            int x = offsetX + rowLabelWidth + c * cellSize;
            gi.drawLine(x, offsetY + topLabelHeight, x, offsetY + topLabelHeight + rows * cellSize);
        }
        for (int r = 0; r <= rows; r++) {
            int y = offsetY + topLabelHeight + r * cellSize;
            gi.drawLine(offsetX + rowLabelWidth, y, offsetX + rowLabelWidth + cols * cellSize, y);
        }

        return gi.getImage();
    }

    public boolean writeImage(Image img, String file_name) {
        return gi.writeImage(img, file_name);
    }

    private List<String> splitEveryNWords(String text, int n) {
        String[] words = text.split(" ");
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            if (i > 0 && i % n == 0) {
                lines.add(line.toString().trim());
                line = new StringBuilder();
            }
            line.append(words[i]).append(" ");
        }

        if (!line.toString().trim().isEmpty()) {
            lines.add(line.toString().trim());
        }

        return lines;
    }

}
