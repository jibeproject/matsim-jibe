package demand;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class TimeDistributions {

    final double[] allDensities = new double[1440];
    private final Map<Integer,double[]> densities = new HashMap<>();

    final Random rand;

    public TimeDistributions(String filePath) throws IOException {
        this.rand = new Random();
        BufferedReader in = new BufferedReader(new FileReader(filePath));
        for(int i = 0 ; i < 1440 ; i++) {
            allDensities[i] = Double.parseDouble(in.readLine());
        }
        in.close();

        // Pre-build arrays
        densities.put(7,buildCandidateArray(7,8));
        densities.put(8,buildCandidateArray(8,9));
        densities.put(9,buildCandidateArray(9,10));
        densities.put(10,buildCandidateArray(10,16));
        densities.put(16,buildCandidateArray(16,17));
        densities.put(17,buildCandidateArray(17,18));
        densities.put(18,buildCandidateArray(18,19));
        densities.put(19,buildCandidateArray(19,7));
    }

    public int sample(int hr) {
        double[] candidates = densities.get(hr);
        double r = rand.nextDouble();

        for(int i = 0 ; i < 1440 ; i++) {
            if(r < candidates[i]) {
                return i * 60 + ((int) (rand.nextDouble() * 60));
            }
        }

        throw new RuntimeException("Could not select integer. Are you sure candidates are cumulative?");
    }

    private double[] buildCandidateArray(int start, int end) {

        start *= 60;
        end *= 60;

        double[] candidates = Arrays.copyOf(allDensities,1440);
        double prev = 0;

        if(start < end) {
            for(int i = 0 ; i < 1440 ; i++) {
                if(i < start || i >= end) {
                    candidates[i] = 0;
                } else {
                    candidates[i] += prev;
                    prev = candidates[i];
                }
            }
        } else {
            for(int i = 0 ; i < 1440 ; i++) {
                if(i < start && i >= end) {
                    candidates[i] = 0;
                } else {
                    candidates[i] += prev;
                    prev = candidates[i];
                }
            }
        }

        for(int i = 0 ; i < 1440 ; i++) {
            candidates[i] /= prev;
        }

        return candidates;
    }


}
