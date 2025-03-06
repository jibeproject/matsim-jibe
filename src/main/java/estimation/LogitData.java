package estimation;

import org.matsim.core.utils.misc.Counter;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class LogitData {

    private final static String SEP = ",";
    private final String filePath;
    private final String choiceVar;
    private final String idVar;
    private Map<String,Integer> colIndex;
    private double[][] predictors;
    private int[] choices;
    private String[] ids;

    public LogitData(String filePath, String choiceVar, String idVar) {
        this.filePath = filePath;
        this.choiceVar = choiceVar;
        this.idVar = idVar;
    }

    public void read() throws IOException {
        Counter counter = new Counter("Processed " + " records.");

        BufferedReader in = new BufferedReader(new FileReader(filePath));
        String recString;

        colIndex = new HashMap<>();

        // Read Header
        recString = in.readLine();
        String[] header = recString.split(SEP);
        int columns = header.length;
        int posChoiceVar = Integer.MAX_VALUE;
        int posIdVar = Integer.MAX_VALUE;
        for (int i = 0 ; i < columns ; i++) {
            String varName = header[i];
            if(varName.equals(choiceVar)) {
                posChoiceVar = i;
            } else if (varName.equals(idVar)) {
                posIdVar = i;
            } else {
                int adj = 0;
                if(i > posIdVar) {
                    adj += 1;
                }
                if(i > posChoiceVar) {
                    adj += 1;
                }
                colIndex.put(varName,i-adj);
            }
        }
        if(posChoiceVar == Integer.MAX_VALUE) {
            throw new RuntimeException("Choice variable \"" + choiceVar + "\" not found!");
        }

        List<Integer> choicesList = new ArrayList<>();
        List<String> idList = new ArrayList<>();
        List<double[]> predictorsList = new ArrayList<>();

        // Read rows
        while ((recString = in.readLine()) != null) {
            counter.incCounter();
            String[] row = recString.split(SEP);
            assert row.length == columns;
            double[] preds = new double[columns-2];
            for(int i = 0 ; i < columns ; i++) {
                if(i == posChoiceVar) {
                    choicesList.add(Integer.parseInt(row[i]));
                } else if (i == posIdVar) {
                    idList.add(row[i]);
                } else {
                    int adj = 0;
                    if (i > posChoiceVar) {
                        adj += 1;
                    }
                    if (i > posIdVar) {
                        adj += 1;
                    }
                    preds[i - adj] = Double.parseDouble(row[i]);
                }
            }
            predictorsList.add(preds);
        }
        in.close();

        predictors = new double[choicesList.size()][columns - 2];
        choices = new int[choicesList.size()];
        ids = new String[choicesList.size()];
        for(int row = 0 ; row < choicesList.size() ; row++) {
            predictors[row] = predictorsList.get(row);
            choices[row] = choicesList.get(row);
            ids[row] = idList.get(row);
        }
    }

    public String[] getIds() { return this.ids; }

    public int[] getChoices() {
        return this.choices;
    }

    public double getValue(int row, String col) {
        Integer colIdx = colIndex.get(col);
        if(colIdx != null) {
            return this.predictors[row][colIdx];
        } else {
            throw new NullPointerException("Column \"" + col + "\" queried but not found in database!");
        }
    }
}
