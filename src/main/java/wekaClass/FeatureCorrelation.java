package wekaClass;

import weka.attributeSelection.CorrelationAttributeEval;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

public class FeatureCorrelation {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: java FeatureCorrelation <dataset.arff>");
            return;
        }

        // Carica il dataset
        DataSource source = new DataSource(args[0]);
        Instances data = source.getDataSet();

        // Imposta la classe target (ultima colonna)
        if (data.classIndex() == -1) {
            data.setClassIndex(data.numAttributes() - 1);
        }

        // Calcola la correlazione tra ciascun attributo e la classe
        CorrelationAttributeEval evaluator = new CorrelationAttributeEval();
        evaluator.buildEvaluator(data);

        System.out.println("Correlazione di ciascun attributo con la classe (bugginess):");
        for (int i = 0; i < data.numAttributes() - 1; i++) {
            if (data.attribute(i).isNumeric()) {
                double corr = evaluator.evaluateAttribute(i);
                System.out.printf("%s -> %.4f%n", data.attribute(i).name(), corr);
            }
        }
    }
}
