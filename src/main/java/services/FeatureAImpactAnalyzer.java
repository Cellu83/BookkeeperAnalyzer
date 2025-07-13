package services;

import java.io.*;
import java.util.*;

public class FeatureAImpactAnalyzer {
    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.out.println("Usage: java FeatureAImpactAnalyzer <predictions_B+.txt> <predictions_B.txt> <actual_A.txt>");
            return;
        }

        List<String> linesBplus = readPredictions(args[0]);
        List<String> linesB = readPredictions(args[1]);
        List<String> linesA = readPredictions(args[2]);

        if (linesBplus.size() != linesB.size()) {
            System.out.println("Files have different number of instances.");
            return;
        }

        int totalInstances = linesBplus.size();

        if (linesA.size() != totalInstances) {
            System.out.println("Mismatch in number of instances between predictions and actuals.");
            return;
        }

        int differentPredictions = 0;

        for (int i = 0; i < totalInstances; i++) {
            String predictedBplus = extractPrediction(linesBplus.get(i));
            String predictedB = extractPrediction(linesB.get(i));
            String actual = extractActual(linesA.get(i));

            if (!predictedBplus.equalsIgnoreCase(predictedB)) {
                differentPredictions++;
                System.out.println("Instance " + (i + 1) + ": B+ predicted " + predictedBplus + ", B predicted " + predictedB + ", Actual: " + actual);
            }
        }

        System.out.println("Total Instances Compared: " + totalInstances);
        System.out.println("Different Predictions: " + differentPredictions);
    }

    private static List<String> readPredictions(String filePath) throws IOException {
        List<String> result = new ArrayList<>();
        boolean capture = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("inst#")) {
                    capture = true;
                    continue;
                }
                if (capture && !line.trim().isEmpty()) {
                    result.add(line);
                }
            }
        }
        return result;
    }

    private static String extractPrediction(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length >= 3) {
            String prediction = parts[2];
            if (prediction.contains(":")) {
                return prediction.split(":")[1];
            }
            return prediction;
        }
        return "?";
    }

    private static String extractActual(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length >= 3) {
            String actual = parts[1];
            if (actual.contains(":")) {
                return actual.split(":")[1];
            }
            return actual;
        }
        return "?";
    }
}