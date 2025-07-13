package wekaClass;

import weka.core.Instances;
import weka.core.Instance;
import weka.core.converters.ConverterUtils.DataSource;
import java.util.Scanner;
import java.io.File;

public class PrintMethodFeatures {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.out.println("Usage: java wekaClass.PrintMethodFeatures <file.arff> <methodName> <commitHash>");
            return;
        }

        String arffPath = args[0];
        String methodToFind = args[1];
        String commitToFind = args[2];

        DataSource source = new DataSource(arffPath);
        Instances data = source.getDataSet();

        int methodIndex = data.attribute("Method").index();
        int releaseIndex = data.attribute("ReleaseId").index();
        int commitIndex = data.attribute("CommitHash").index();
        int buggyIndex = data.attribute("Buggy").index();

        boolean found = false;
        for (int i = 0; i < data.numInstances(); i++) {
            Instance inst = data.instance(i);
            if (inst.stringValue(methodIndex).equals(methodToFind) &&
                inst.toString(commitIndex).equals(commitToFind)) {
                found = true;
                System.out.println("Metodo trovato: " + methodToFind);
                System.out.println("Commit: " + inst.toString(commitIndex));
                System.out.println("Bugginess: " + inst.stringValue(buggyIndex));
                System.out.println("Feature:");
                for (int j = 0; j < data.numAttributes(); j++) {
                    if (j != methodIndex && j != releaseIndex && j != buggyIndex) {
                        String attrName = data.attribute(j).name();
                        if (data.attribute(j).isNumeric()) {
                            System.out.println(attrName + ": " + inst.value(j));
                        } else {
                            System.out.println(attrName + ": " + inst.stringValue(j));
                        }
                    }
                }
                break;
            }
        }

        if (!found) {
            System.out.println("Metodo non trovato: " + methodToFind + " nel commit " + commitToFind);
        }
    }
}
