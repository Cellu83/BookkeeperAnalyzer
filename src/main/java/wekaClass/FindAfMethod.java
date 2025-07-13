package wekaClass;

import weka.core.Instances;
import weka.core.Instance;
import weka.core.converters.ConverterUtils.DataSource;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

public class FindAfMethod {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: java FindAfMethod <dataset.arff>");
            return;
        }

        DataSource source = new DataSource(args[0]);
        Instances data = source.getDataSet();
        if (data.classIndex() == -1)
            data.setClassIndex(data.numAttributes() - 1);

        if (data.attribute("ReleaseId") == null || data.attribute("Method") == null ||
            data.attribute("Smells") == null || data.attribute("Buggy") == null ||
            data.attribute("CommitHash") == null) {
            System.out.println("Errore: uno o più attributi non trovati nel file ARFF.");
            System.out.println("Assicurati che gli attributi si chiamino esattamente: ReleaseId, Method, Smells, Buggy, CommitHash.");
            return;
        }

        int releaseIndex = data.attribute("ReleaseId").index();
        int methodIndex = data.attribute("Method").index();
        int smellsIndex = data.attribute("Smells").index();
        int bugginessIndex = data.attribute("Buggy").index();
        int commitIndex = data.attribute("CommitHash").index();

        // Trova la release massima (nominale, quindi confrontiamo come stringa)
        String maxRelease = "";
        for (int i = 0; i < data.numInstances(); i++) {
            String release = data.instance(i).stringValue(releaseIndex);
            if (release.compareTo(maxRelease) > 0)
                maxRelease = release;
        }

        // Cerca tra i buggy della release massima il metodo con massimo Smells
        double maxSmells = -1;
        Instance afMethod = null;

        for (int i = 0; i < data.numInstances(); i++) {
            Instance inst = data.instance(i);
            String release = inst.stringValue(releaseIndex);
            String buggy = inst.stringValue(bugginessIndex);
            if (release.equals(maxRelease) && buggy.equalsIgnoreCase("YES")) {
                double smells = inst.value(smellsIndex);
                if (smells > maxSmells) {
                    maxSmells = smells;
                    afMethod = inst;
                }
            }
        }

        if (afMethod != null) {
            System.out.println("AFMethod trovato:");
            System.out.println("Metodo: " + afMethod.stringValue(methodIndex));
            System.out.println("Release: " + afMethod.stringValue(releaseIndex));
            System.out.println("NSmells: " + afMethod.value(smellsIndex));
            System.out.println("Bugginess: " + afMethod.stringValue(bugginessIndex));
            System.out.println("Commit: " + afMethod.stringValue(commitIndex));

            System.out.println("\nTutte le feature di AFMethod:");
            for (int i = 0; i < afMethod.numAttributes(); i++) {
                if (i != methodIndex && i != releaseIndex && i != bugginessIndex) {
                    String attrName = data.attribute(i).name();
                    String value;
                    if (data.attribute(i).isNumeric()) {
                        value = String.valueOf(afMethod.value(i));
                    } else {
                        value = afMethod.stringValue(i);
                    }
                    System.out.println(attrName + ": " + value);
                }
            }

            int fileIndex = data.attribute("File").index();

            // Salvataggio CSV delle feature di AFMethod

            String methodName = afMethod.stringValue(methodIndex).replaceAll("[^a-zA-Z0-9_\\-]", "_");
            File csvFile = new File("AFMethod_" + methodName + ".csv");
            try (PrintWriter pw = new PrintWriter(new FileWriter(csvFile))) {
                pw.println("Attribute,Value");
                for (int i = 0; i < afMethod.numAttributes(); i++) {
                    String attrName = data.attribute(i).name();
                    String value;
                    if (data.attribute(i).isNumeric()) {
                        value = String.valueOf(afMethod.value(i));
                    } else {
                        value = afMethod.stringValue(i);
                    }
                    pw.println(attrName + "," + value);
                }
                System.out.println("CSV salvato in: " + csvFile.getAbsolutePath());
            } catch (Exception e) {
                System.out.println("Errore durante il salvataggio del CSV: " + e.getMessage());
            }
        } else {
            System.out.println("Nessun metodo buggy trovato nella release più recente.");
        }
    }
}
