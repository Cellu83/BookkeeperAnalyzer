package wekaClass;

import weka.attributeSelection.CfsSubsetEval;
import weka.attributeSelection.GreedyStepwise;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.core.converters.ArffSaver;
import java.io.File;
import weka.core.converters.ConverterUtils.DataSource;

public class AttrSelection {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Usage: java wekaClass.AttrSelection input.arff output.arff");
            return;
        }

        String inputPath = args[0];
        String outputPath = args[1];

        DataSource source = new DataSource(inputPath);
        Instances dataset = source.getDataSet();

        AttributeSelection filter = new AttributeSelection();
        CfsSubsetEval eval = new CfsSubsetEval();
        GreedyStepwise search = new GreedyStepwise();
        search.setSearchBackwards(true);

        filter.setEvaluator(eval);
        filter.setSearch(search);
        filter.setInputFormat(dataset);

        Instances newData = Filter.useFilter(dataset, filter);

        ArffSaver saver = new ArffSaver();
        saver.setInstances(newData);
        saver.setFile(new File(outputPath));
        saver.writeBatch();

        System.out.println("Attribute selection completed and saved to " + outputPath);
    }
}
