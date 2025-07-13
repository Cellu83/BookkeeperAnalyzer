package wekaClass;

import weka.core.Instances;
import weka.core.converters.ArffSaver;
import java.io.File;
import weka.core.converters.ConverterUtils.DataSource;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;

public class AttributeFilter {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.out.println("Usage: java wekaClass.AttributeFilter input.arff output.arff attributeIndexToRemove");
            return;
        }

        String inputPath = args[0];
        String outputPath = args[1];
        String attributeIndex = args[2]; // e.g., "1" to remove the first attribute

        DataSource source = new DataSource(inputPath);
        Instances dataset = source.getDataSet();

        String[] opts = new String[]{ "-R", attributeIndex };
        Remove remove = new Remove();
        remove.setOptions(opts);
        remove.setInputFormat(dataset);
        Instances newData = Filter.useFilter(dataset, remove);

        ArffSaver saver = new ArffSaver();
        saver.setInstances(newData);
        saver.setFile(new File(outputPath));
        saver.writeBatch();

        System.out.println("Filtered dataset saved to " + outputPath);
    }
}
