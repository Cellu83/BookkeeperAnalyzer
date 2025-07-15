package services;

import java.io.*;
import java.util.*;

public class FeatureAImpactAnalyzer {
    /**
     * Punto di ingresso del programma. Confronta le predizioni dei file B+ e B con le etichette reali
     * contenute in A. Stampa i casi in cui le predizioni di B+ e B differiscono e riporta il totale.
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.out.println("Usage: java FeatureAImpactAnalyzer <predictions_B+.txt> <predictions_B.txt> <actual_A.txt>");
            return;
        }

        // Lista delle predizioni dal file B+
        List<String> linesBplus = readPredictions(args[0]);
        // Lista delle predizioni dal file B
        List<String> linesB = readPredictions(args[1]);
        // Lista dei valori reali dal file A
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

    /**
     * Legge le predizioni da un file Weka di output. Salta le intestazioni e cattura solo le righe con predizioni.
     * @param filePath percorso del file .txt contenente le predizioni Weka
     * @return lista delle righe di predizione rilevanti
     */
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

    /**
     * Estrae la predizione dalla riga data. La predizione è presente nella terza colonna (indice 2).
     * @param line riga del file Weka
     * @return classe predetta ("YES" o "NO")
     */
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

    /**
     * Estrae il valore reale (etichetta) dalla riga data. Si trova nella seconda colonna (indice 1).
     * @param line riga del file Weka
     * @return classe reale ("YES" o "NO")
     */
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