package services;

import java.io.*;
import java.util.*;

public class GenerateB {
    public static void main(String[] args) {
        String inputFile = "B+.csv";
        String outputFile = "B.csv";

        try (
            BufferedReader br = new BufferedReader(new FileReader(inputFile));
            BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile))
        ) {
            String headerLine = br.readLine();
            if (headerLine == null) {
                System.err.println("File vuoto.");
                return;
            }

            // Scrivi header
            bw.write(headerLine);
            bw.newLine();

            String[] headers = headerLine.split(",");
            int smellIndex = -1;

            for (int i = 0; i < headers.length; i++) {
                if (headers[i].equalsIgnoreCase("Smells") || headers[i].equalsIgnoreCase("NSmells")) {
                    smellIndex = i;
                    break;
                }
            }

            if (smellIndex == -1) {
                System.err.println("Colonna 'Smells' o 'NSmells' non trovata.");
                return;
            }

            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",", -1);
                values[smellIndex] = "0"; // sovrascrivi il valore di Smells
                bw.write(String.join(",", values));
                bw.newLine();
            }

            System.out.println("File B.csv generato con successo.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
