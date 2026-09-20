package bruxism_grapher2;


import java.util.ArrayList;
import java.util.Arrays;

public class CorrelationsCalculator {

    public CorrelationsCalculator(String summary_complete_file_path){
        readSummary(summary_complete_file_path);
    }

    public static final byte PositiveCorr = 1, NegativeCorr = 2, NeutralCorr = 0;

    static ArrayList<String> positiveIncreased = new ArrayList<>(Arrays.asList(
            "Stopped after beep %",
            "Average clenching event pause (minutes)",
            "Stopped after beep"
    ));
    static ArrayList<String> negativeIncreased = new ArrayList<>(Arrays.asList(
            "Total clench time (seconds)",
            "Active time (permille)",
            "Clenching Rate (per hour)",
            "Avg beeps per event",
            "Average clenching duration (seconds)",
            "Jaw Events",
            "Beep Count",
            "Alarm Triggers",
            "Alarm %"
    ));

    public static byte isGoingToBetter(double correlation, String tablelabel) {
        boolean cc1 = positiveIncreased.contains(tablelabel);
        boolean cc2 = negativeIncreased.contains(tablelabel);
        boolean c1 = correlation > 0;
        boolean c2 = correlation < 0;
        boolean b1 = c1 && cc1;
        boolean b2 = c2 && cc2;
        return !(cc1||cc2) ? NeutralCorr : (b1 || b2 ? PositiveCorr : NegativeCorr);
    }

    // NEW: Unified Data Container
    public static class CorrelationResult {
        public double r = 0;
        public double p_value = 1.0;
        public double effect_size = 0;
        public byte effect = NeutralCorr;
    }

    int infoindex = -1;
    String[] summaryTitles;
    ArrayList<String[]> summaryTuples;
    ArrayList<String> filterNames;
    int[] filterhitcount;
    ArrayList<String> statNames;

    void readSummary(String summary_complete_file_path) {
        SummaryReader.setFilepath(summary_complete_file_path);
        SummaryReader sr = SummaryReader.getInstance();
        summaryTitles = sr.getSummaryTitles();
        summaryTuples = sr.getSummaryTuplesWithNoSkipItems();
        filterNames = sr.getFilterNames();
        infoindex = sr.getInfomationIndex();
    }

    public ArrayList<String> getFilterNames(){ return filterNames; }
    public ArrayList<String> getStatNames(){ return statNames; }
    public int[] getFilterhitcount(){ return filterhitcount; }

    // UPGRADED: Returns the full CorrelationResult matrix
    public CorrelationResult[][] makeCorrelationMatrix() {
        int effectivedatalength = !summaryTuples.isEmpty() ? summaryTuples.get(0).length-3 : 0;
        int startcolumn = 1;

        double[][] filterstats = new double[filterNames.size()][summaryTuples.size()];
        double[][] entries = new double[effectivedatalength][summaryTuples.size()];
        filterhitcount = new int[filterNames.size()];
        statNames = new ArrayList<>();

        for(int chartelement = 0; chartelement < effectivedatalength; chartelement++) {
            statNames.add(summaryTitles[startcolumn+chartelement]);
            for (int tuple = 0; tuple < summaryTuples.size(); tuple++) {
                entries[chartelement][tuple] = Double.parseDouble(summaryTuples.get(tuple)[chartelement+startcolumn].replace(",","."));
            }
        }

        double[][] filter_average = new double[filterNames.size()][effectivedatalength];
        double[][] no_filter_average = new double[filterNames.size()][effectivedatalength];

        for(int filter = 0; filter < filterNames.size(); filter++){
            for (int tuple = 0; tuple < summaryTuples.size(); tuple++) {
                boolean hit = summaryTuples.get(tuple)[infoindex].contains(filterNames.get(filter));
                filterstats[filter][tuple] = hit ? 1.0 : 0.0;

                for(int chartelement = 0; chartelement < effectivedatalength; chartelement++) {
                    if (hit) filter_average[filter][chartelement] += entries[chartelement][tuple];
                    else no_filter_average[filter][chartelement] += entries[chartelement][tuple];
                }
                if(hit) filterhitcount[filter]++;
            }

            int noFilterHitCount = summaryTuples.size() - filterhitcount[filter];
            for (int chartelement = 0; chartelement < effectivedatalength; chartelement++) {
                if (filterhitcount[filter] > 0) filter_average[filter][chartelement] /= filterhitcount[filter];
                if (noFilterHitCount > 0) no_filter_average[filter][chartelement] /= noFilterHitCount;
            }
        }

        CorrelationResult[][] results = new CorrelationResult[filterNames.size()][effectivedatalength];

        for(int filter = 0; filter < filterNames.size(); filter++) {
            for (int chartelement = 0; chartelement < effectivedatalength; chartelement++) {
                CorrelationResult res = new CorrelationResult();

                res.r = ((int)(Correlations.pearsonCorrelation(entries[chartelement], filterstats[filter])*100.0))/100.0;
                res.p_value = Correlations.CorrelationSignificance.correlationPValue(res.r, summaryTuples.size());
                res.effect = isGoingToBetter(res.r, statNames.get(chartelement));

                if(no_filter_average[filter][chartelement] != 0) {
                    res.effect_size = ((filter_average[filter][chartelement] - no_filter_average[filter][chartelement]) / no_filter_average[filter][chartelement]) * 100.0;
                }
                results[filter][chartelement] = res;
            }
        }
        return results;
    }
}