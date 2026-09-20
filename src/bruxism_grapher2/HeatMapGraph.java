package bruxism_grapher2;

import bruxism_grapher2.grapher_interfaces.GrapherInterface;
import bruxism_grapher2.grapher_interfaces.IconManager;
import bruxism_grapher2.grapher_interfaces.TaskRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HeatMapGraph<Image, Color, Font> {

    private GrapherInterface<Color, Image, Font> gi;
    private IconManager<Color, Image> icm;
    private TaskRunner taskRunner;
    private CorrelationsCalculator correlationsCalculator;

    public int graph_width = 1280;
    public int graph_height = 1280;

    public void setPlatformSpecificAbstractions(GrapherInterface<Color, Image, Font> g, IconManager<Color, Image> im, TaskRunner tr) {
        gi = g;
        icm = im;
        taskRunner = tr;
        correlationsCalculator = new CorrelationsCalculator(gi.getRecordingsPath().getPath() + "/Summary/Summary.csv");
    }

    public Image generateGraphCorrelations() {
        return generateGraph(correlationsCalculator.makeCorrelationMatrix(), correlationsCalculator.getFilterNames(), correlationsCalculator.getStatNames());
    }

    /**
     * Generates a gradient color based on correlation strength and clinical effect.
     */
    private Color getGradientColor(double correlation, byte effect) {
        if (Double.isNaN(correlation)) return gi.convertColor("#E0E0E0");

        double strength = Math.min(1.0, Math.abs(correlation));
        int offColor = (int) (240 * (1.0 - strength));
        int onColor = (int) (240 - (40 * strength));

        int r = 240, g = 240, b = 240;

        if (effect == CorrelationsCalculator.PositiveCorr) {
            r = offColor; g = onColor; b = offColor;
        } else if (effect == CorrelationsCalculator.NegativeCorr) {
            r = onColor; g = offColor; b = offColor;
        } else {
            r = offColor; g = offColor; b = onColor;
        }

        return gi.convertColor(String.format(Locale.US, "#%02X%02X%02X", r, g, b));
    }

    /**
     * Main rendering method for the Heatmap.
     */
    public Image generateGraph(CorrelationsCalculator.CorrelationResult[][] data, ArrayList<String> tag_names, ArrayList<String> data_names) {
        final double threshold = 0.25;
        final String[] evals = {"Negative", "Mostly negative", "Neutral", "Mostly positive", "Positive"};

        int rows = tag_names.size();
        int cols = data_names.size();

        gi.setImageSize(graph_width, graph_height);
        gi.setColor(gi.convertColor("#FFFFFF"));
        gi.fillRect(0, 0, graph_width, graph_height);

        if (rows == 0 || cols == 0) return gi.getImage();

        // Calculate dynamic dimensions
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

        gi.setFont("Arial", fontSize);
        int[] hitCounts = correlationsCalculator.getFilterhitcount();

        // 1. Draw Top Column Labels (Metrics)
        for (int c = 0; c < cols; c++) {
            List<String> lines = splitEveryNWords(data_names.get(c), 3);
            int baseX = offsetX + rowLabelWidth + c * cellSize + (cellSize / 2);
            int baseY = offsetY + topLabelHeight - 10;
            for (int i = 0; i < lines.size(); i++) {
                int x = baseX + i * (fontSize / 2);
                gi.setColor(gi.convertColor("#333333"));
                gi.drawRotatedString(lines.get(i), x, baseY, -90);
            }
        }

        // 2. Render Heatmap Cells and Overall Evaluation
        for (int r = 0; r < rows; r++) {
            int y = offsetY + topLabelHeight + r * cellSize;
            int posNegScore = 0;
            boolean enoughData = hitCounts[r] >= 3;

            for (int c = 0; c < cols; c++) {
                int x = offsetX + rowLabelWidth + c * cellSize;
                CorrelationsCalculator.CorrelationResult cell = data[r][c];

                // Render Invalid/Insignificant Cell
                if (!enoughData || Double.isNaN(cell.r) || cell.p_value > 0.05) {
                    drawBlankCell(x, y, cellSize);
                    continue;
                }

                // Render Valid Data Cell
                byte effect = cell.effect;
                if (Math.abs(cell.r) < threshold) effect = CorrelationsCalculator.NeutralCorr;
                else {
                    posNegScore += (effect == CorrelationsCalculator.PositiveCorr) ? 1 :
                            (effect == CorrelationsCalculator.NegativeCorr ? -1 : 0);
                }

                gi.setColor(getGradientColor(cell.r, effect));
                gi.fillRect(x, y, cellSize, cellSize);

                // Print correlation coefficient inside cell if large enough
                if (cellSize >= 40) {
                    gi.setColor(gi.convertColor(Math.abs(cell.r) > 0.6 ? "#FFFFFF" : "#000000"));
                    gi.drawString(String.format(Locale.US, "%.2f", cell.r), x + cellSize / 5, y + cellSize / 2 + 5);
                }
            }

            // Render Evaluation Label (Far Right)
            drawEvaluationLabel(posNegScore, enoughData, evals, offsetX + rowLabelWidth + cols * cellSize + 15, y + cellSize / 2 + 5);
        }

        // 3. Draw Left Row Labels (Tags)
        for (int r = 0; r < rows; r++) {
            List<String> lines = splitEveryNWords(tag_names.get(r), 2);
            int baseY = offsetY + topLabelHeight + r * cellSize + (cellSize / 2) - (lines.size() * fontSize / 4);

            for (int i = 0; i < lines.size(); i++) {
                String lineText = lines.get(i);
                gi.setColor(gi.convertColor("#333333"));

                // Get the width of the rendered text
                int textWidth = gi.getStringWidth(lineText);

                // Calculate the right-aligned X position.
                // (offsetX + rowLabelWidth) is exactly where the heatmap grid starts.
                // We subtract textWidth and an extra 10 pixels for a clean margin.
                int rightAlignedX = (offsetX + rowLabelWidth) - textWidth - 10;

                gi.drawString(lineText, rightAlignedX, baseY + i * fontSize);
            }
        }

        // 4. Draw Grid Overlay
        drawGridOverlay(offsetX, offsetY, rows, cols, cellSize, rowLabelWidth, topLabelHeight);

        return gi.getImage();
    }

    private void drawBlankCell(int x, int y, int cellSize) {
        gi.setColor(gi.convertColor("#F5F5F5"));
        gi.fillRect(x, y, cellSize, cellSize);
        gi.setColor(gi.convertColor("#DDDDDD"));
        gi.drawLine(x, y, x + cellSize, y + cellSize);
    }

    private void drawEvaluationLabel(int posNegScore, boolean enoughData, String[] evals, int x, int y) {
        int ab = Math.abs(posNegScore);
        boolean positive = posNegScore > 0;
        int selected = ab < 2 ? 2 : ab < 3 ? (positive ? 3 : 1) : (positive ? 4 : 0);

        String evalLabel = enoughData ? evals[selected] : "Need more data";
        String evalColor = !enoughData ? "#999999" : (selected == 4 ? "#22AA22" : selected == 3 ? "#66CC66"
                                                                                  : selected == 2 ? "#4444CC" : selected == 1 ? "#CC6666" : "#AA2222");

        gi.setColor(gi.convertColor(evalColor));
        gi.drawString(evalLabel, x, y);
    }

    private void drawGridOverlay(int offsetX, int offsetY, int rows, int cols, int cellSize, int rowLabelWidth, int topLabelHeight) {
        gi.setColor(gi.convertColor("#DDDDDD"));
        for (int c = 0; c <= cols; c++) {
            int x = offsetX + rowLabelWidth + c * cellSize;
            gi.drawLine(x, offsetY + topLabelHeight, x, offsetY + topLabelHeight + rows * cellSize);
        }
        for (int r = 0; r <= rows; r++) {
            int y = offsetY + topLabelHeight + r * cellSize;
            gi.drawLine(offsetX + rowLabelWidth, y, offsetX + rowLabelWidth + cols * cellSize, y);
        }

        // Draw strong outline
        gi.setColor(gi.convertColor("#999999"));
        gi.drawRect(offsetX + rowLabelWidth, offsetY + topLabelHeight, cols * cellSize, rows * cellSize);
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
        if (!line.toString().trim().isEmpty()) lines.add(line.toString().trim());
        return lines;
    }
}