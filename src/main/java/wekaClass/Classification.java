package wekaClass;

import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.trees.J48;
import weka.classifiers.functions.SMO;

public class Classification {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: java wekaClass.Classification dataset.arff");
            return;
        }

        String datasetPath = args[0];
        DataSource source = new DataSource(datasetPath);
        Instances dataset = source.getDataSet();

        if (dataset.classIndex() == -1) {
            dataset.setClassIndex(dataset.numAttributes() - 1);
        }

        // NaiveBayes
        NaiveBayes nb = new NaiveBayes();
        nb.buildClassifier(dataset);
        System.out.println("=== NaiveBayes Capabilities ===");
        System.out.println(nb.getCapabilities().toString());

        // SMO (SVM)
        SMO svm = new SMO();
        svm.buildClassifier(dataset);
        System.out.println("=== SMO Capabilities ===");
        System.out.println(svm.getCapabilities().toString());

        // J48
        String[] options = { "-C", "0.11", "-M", "3" };
        J48 tree = new J48();
        tree.setOptions(options);
        tree.buildClassifier(dataset);
        System.out.println("=== J48 Capabilities ===");
        System.out.println(tree.getCapabilities().toString());
        System.out.println("=== J48 Tree ===");
        System.out.println(tree.graph());
    }
}
