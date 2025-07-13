package wekaClass;

import weka.core.Instances;
import weka.core.converters.ArffLoader;
import weka.core.converters.CSVSaver;

import java.io.File;

public class Arff2CSV {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Usage: java wekaClass.Arff2CSV input.arff output.csv");
            return;
        }

        String inputPath = args[0];
        String outputPath = args[1];

        // Load ARFF file
        ArffLoader loader = new ArffLoader();
        loader.setSource(new File(inputPath));
        Instances data = loader.getDataSet();

        // Save as CSV
        CSVSaver saver = new CSVSaver();
        saver.setInstances(data);
        saver.setFile(new File(outputPath));
        saver.writeBatch();

        System.out.println("Conversion completed: " + outputPath);
    }
}
