package services;

import java.io.*;

public class B_CClasses {
    /**
     * Splits a CSV file into two files based on the value in the "Smells" column.
     * Rows with "Smells" == "0" go to noSmellCsv, others to hasSmellCsv.
     *
     *      */
    public static void splitCSVBySmells(String inputCsv, String noSmellCsv, String hasSmellCsv) {
        try (
            BufferedReader reader = new BufferedReader(new FileReader(inputCsv));
            BufferedWriter writerNoSmell = new BufferedWriter(new FileWriter(noSmellCsv));
            BufferedWriter writerHasSmell = new BufferedWriter(new FileWriter(hasSmellCsv))
        ) {
            String header = reader.readLine();
            if (header == null) throw new IOException("Empty CSV file");
            writerNoSmell.write(header);
            writerNoSmell.newLine();
            writerHasSmell.write(header);
            writerHasSmell.newLine();

            String[] headers = header.split(",");
            int smellIndex = -1;
            for (int i = 0; i < headers.length; i++) {
                if (headers[i].trim().equalsIgnoreCase("Smells")) {
                    smellIndex = i;
                    break;
                }
            }
            if (smellIndex == -1) throw new IOException("Smells column not found");

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length > smellIndex && parts[smellIndex].trim().equals("0")) {
                    writerNoSmell.write(line);
                    writerNoSmell.newLine();
                } else {
                    writerHasSmell.write(line);
                    writerHasSmell.newLine();
                }
            }
        } catch (IOException e) {
            System.err.println("Error processing CSV: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java services.B_CClasses <input.csv> <no_smells.csv> <has_smells.csv>");
            return;
        }
        splitCSVBySmells(args[0], args[1], args[2]);
    }
}
