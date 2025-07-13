package wekaClass;

import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;
import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.trees.RandomForest;
import weka.classifiers.Evaluation;
import weka.classifiers.AbstractClassifier;

import java.util.Random;
import java.io.FileWriter;
import java.io.PrintWriter;

public class ClassifierComparison {

    public static void main(String[] args) throws Exception {
        // String datasetPath = args[0]; // rimosso l'uso da command line
        String datasetPath = "/Users/colaf/Documents/ISW2/BookkeeperAnalyzer/metrics_bookkeeper.arff"; // <-- Inserisci qui il path completo o relativo
        DataSource source = new DataSource(datasetPath);
        Instances data = source.getDataSet();
        if (data == null) {
            System.err.println("Errore: il file non è stato caricato correttamente. Controlla il percorso: " + datasetPath);
            return;
        }
        if (data.classIndex() == -1) {
            data.setClassIndex(data.numAttributes() - 1);
        }

        Classifier[] classifiers = {
            new RandomForest(),
            new NaiveBayes(),
            new IBk()
        };

        String[] classifierNames = {
            "RandomForest",
            "NaiveBayes",
            "IBk"
        };

        int numFolds = 10;
        int numRuns = 10;
        Random rand = new Random(1);

        PrintWriter writer = new PrintWriter(new FileWriter("classification_results.csv"));
        writer.println("Classifier,Accuracy,Precision,Recall,F-Measure,AUC,Kappa,NPofB20");

        for (int i = 0; i < classifiers.length; i++) {
            System.out.println("=== Evaluating: " + classifierNames[i] + " ===");

            double totalAccuracy = 0, totalPrecision = 0, totalRecall = 0;
            double totalFMeasure = 0, totalAUC = 0, totalKappa = 0;
            java.util.List<double[]> probAndLabelList = new java.util.ArrayList<>();

            for (int run = 0; run < numRuns; run++) {
                Instances randData = new Instances(data);
                randData.randomize(new Random(run));
                if (randData.classAttribute().isNominal()) {
                    randData.stratify(numFolds);
                }

                for (int n = 0; n < numFolds; n++) {
                    System.out.printf("→ %s - Run %d/%d - Fold %d/%d%n", classifierNames[i], run + 1, numRuns, n + 1, numFolds);
                    Instances train = randData.trainCV(numFolds, n);
                    Instances test = randData.testCV(numFolds, n);

                    Classifier clsCopy = AbstractClassifier.makeCopy(classifiers[i]);
                    clsCopy.buildClassifier(train);
                    Evaluation eval = new Evaluation(train);
                    eval.evaluateModel(clsCopy, test);

                    totalAccuracy += eval.pctCorrect();
                    totalPrecision += eval.precision(1);
                    totalRecall += eval.recall(1);
                    totalFMeasure += eval.fMeasure(1);
                    totalAUC += eval.areaUnderROC(1);
                    totalKappa += eval.kappa();

                    for (int j = 0; j < test.numInstances(); j++) {
                        weka.core.Instance inst = test.instance(j);
                        double[] dist = clsCopy.distributionForInstance(inst);
                        double actual = inst.classValue(); // 1.0 se YES
                        probAndLabelList.add(new double[]{dist[1], actual == 1.0 ? 1.0 : 0.0});
                    }
                }
            }

            System.out.printf("Average Accuracy: %.2f%%\n", totalAccuracy / (numRuns * numFolds));
            System.out.printf("Average Precision: %.4f\n", totalPrecision / (numRuns * numFolds));
            System.out.printf("Average Recall: %.4f\n", totalRecall / (numRuns * numFolds));
            System.out.printf("Average F-Measure: %.4f\n", totalFMeasure / (numRuns * numFolds));
            System.out.printf("Average AUC: %.4f\n", totalAUC / (numRuns * numFolds));
            System.out.printf("Average Kappa: %.4f\n", totalKappa / (numRuns * numFolds));

            probAndLabelList.sort((a, b) -> Double.compare(b[0], a[0]));
            int topN = (int)(probAndLabelList.size() * 0.2);
            int buggyInTop = 0;
            for (int iTop = 0; iTop < topN; iTop++) {
                if (probAndLabelList.get(iTop)[1] == 1.0) buggyInTop++;
            }
            double npofb20 = (double) buggyInTop / topN;
            System.out.printf("NPofB20: %.4f\n", npofb20);

            System.out.println();

            writer.printf("%s,%.2f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f%n",
                classifierNames[i],
                totalAccuracy / (numRuns * numFolds),
                totalPrecision / (numRuns * numFolds),
                totalRecall / (numRuns * numFolds),
                totalFMeasure / (numRuns * numFolds),
                totalAUC / (numRuns * numFolds),
                totalKappa / (numRuns * numFolds),
                npofb20);
        }
        writer.close();
    }
}
