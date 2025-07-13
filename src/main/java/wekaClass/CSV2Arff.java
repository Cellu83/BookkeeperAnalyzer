package wekaClass;

import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.core.converters.ArffSaver;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;

import java.io.File;

public class CSV2Arff {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Usage: java CSV2Arff input.csv output.arff");
            return;
        }

        // Carica il CSV
        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(args[0]));
        Instances data = loader.getDataSet();

        // Converti la prima colonna (1) da stringa a nominale (se necessario)
        weka.filters.unsupervised.attribute.StringToNominal stn = new weka.filters.unsupervised.attribute.StringToNominal();
        stn.setAttributeRange("1"); // Prima colonna
        stn.setInputFormat(data);
        data = Filter.useFilter(data, stn);


        // Salva in ARFF
        ArffSaver saver = new ArffSaver();
        saver.setInstances(data);
        saver.setFile(new File(args[1]));
        saver.writeBatch();

        System.out.println("Conversion completed: " + args[1]);
    }
}
