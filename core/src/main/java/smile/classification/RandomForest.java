/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.classification;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.infinispan.creson.AtomicMatrix;
import org.infinispan.creson.Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.data.Attribute;
import smile.data.AttributeDataset;
import smile.math.Math;
import smile.util.MulticoreExecutor;
import smile.util.ServerlessExecutor;
import smile.util.SmileUtils;
import smile.util.Stream;
import smile.validation.Accuracy;
import smile.validation.ClassificationMeasure;

/**
 * Random forest for classification. Random forest is an ensemble classifier
 * that consists of many decision trees and outputs the majority vote of
 * individual trees. The method combines bagging idea and the random
 * selection of features.
 * <p>
 * Each tree is constructed using the following algorithm:
 * <ol>
 * <li> If the number of cases in the training set is N, randomly sample N cases
 * with replacement from the original data. This sample will
 * be the training set for growing the tree. 
 * <li> If there are M input variables, a number m &lt;&lt; M is specified such
 * that at each node, m variables are selected at random out of the M and
 * the best split on these m is used to split the node. The value of m is
 * held constant during the forest growing. 
 * <li> Each tree is grown to the largest extent possible. There is no pruning. 
 * </ol>
 * The advantages of random forest are:
 * <ul>
 * <li> For many data sets, it produces a highly accurate classifier.
 * <li> It runs efficiently on large data sets.
 * <li> It can handle thousands of input variables without variable deletion.
 * <li> It gives estimates of what variables are important in the classification.
 * <li> It generates an internal unbiased estimate of the generalization error
 * as the forest building progresses.
 * <li> It has an effective method for estimating missing data and maintains
 * accuracy when a large proportion of the data are missing.
 * </ul>
 * The disadvantages are
 * <ul>
 * <li> Random forests are prone to over-fitting for some datasets. This is
 * even more pronounced on noisy data.
 * <li> For data including categorical variables with different number of
 * levels, random forests are biased in favor of those attributes with more
 * levels. Therefore, the variable importance scores from random forest are
 * not reliable for this type of data.
 * </ul>
 * 
 * @author Haifeng Li
 */
public class RandomForest implements SoftClassifier<double[]> {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(RandomForest.class);

    /**
     * Decision tree wrapper with a weight. Currently, the weight is the accuracy of
     * tree on the OOB samples, which can be used when aggregating
     * tree votes.
     */
    static class Tree implements Serializable {
        DecisionTree tree;
        double weight;
        Tree(DecisionTree tree, double weight) {
            this.tree = tree;
            this.weight = weight;
        }
    }
    /**
     * Forest of decision trees. The second value is the accuracy of
     * tree on the OOB samples, which can be used a weight when aggregating
     * tree votes.
     */
    private List<Tree> trees;
    /**
     * The number of classes.
     */
    private int k = 2;
    /**
     * Out-of-bag estimation of error rate, which is quite accurate given that
     * enough trees have been grown (otherwise the OOB estimate can
     * bias upward).
     */
    private double error;
    /**
     * Variable importance. Every time a split of a node is made on variable
     * the (GINI, information gain, etc.) impurity criterion for the two
     * descendent nodes is less than the parent node. Adding up the decreases
     * for each individual variable over all trees in the forest gives a fast
     * variable importance that is often very consistent with the permutation
     * importance measure.
     */
    private double[] importance;

    /**
     * Trainer for random forest classifiers.
     */
    public static class Trainer extends ClassifierTrainer<double[]> {
        /**
         * The number of trees.
         */
        private int ntrees = 500;
        /**
         * The splitting rule.
         */
        private DecisionTree.SplitRule rule = DecisionTree.SplitRule.GINI;
        /**
         * The number of random selected features to be used to determine the decision
         * at a node of the tree. floor(sqrt(dim)) seems to give generally good performance,
         * where dim is the number of variables.        
         */
        private int mtry = -1;
        /**
         * The minimum size of leaf nodes.
         */
        private int nodeSize = 1;
        /**
         * The maximum number of leaf nodes.
         */
        private int maxNodes = 100;
        /**
         * The sampling rate.
         */
        private double subsample = 1.0;

        /**
         * Default constructor of 500 trees.
         */
        public Trainer() {

        }

        /**
         * Constructor.
         * 
         * @param ntrees the number of trees.
         */
        public Trainer(int ntrees) {
            if (ntrees < 1) {
                throw new IllegalArgumentException("Invalid number of trees: " + ntrees);
            }

            this.ntrees = ntrees;
        }

        /**
         * Constructor.
         *
         * @param attributes the attributes of independent variable.
         * @param ntrees the number of trees.
         */
        public Trainer(Attribute[] attributes, int ntrees) {
            super(attributes);

            if (ntrees < 1) {
                throw new IllegalArgumentException("Invalid number of trees: " + ntrees);
            }

            this.ntrees = ntrees;
        }

        /**
         * Sets the splitting rule.
         * @param rule the splitting rule.
         */
        public Trainer setSplitRule(DecisionTree.SplitRule rule) {
            this.rule = rule;
            return this;
        }

        /**
         * Sets the number of trees in the random forest.
         * @param ntrees the number of trees.
         */
        public Trainer setNumTrees(int ntrees) {
            if (ntrees < 1) {
                throw new IllegalArgumentException("Invalid number of trees: " + ntrees);
            }

            this.ntrees = ntrees;
            return this;
        }

        /**
         * Sets the number of random selected features for splitting.
         * @param mtry the number of random selected features to be used to determine
         * the decision at a node of the tree. floor(sqrt(p)) seems to give
         * generally good performance, where p is the number of variables.
         */
        public Trainer setNumRandomFeatures(int mtry) {
            if (mtry < 1) {
                throw new IllegalArgumentException("Invalid number of random selected features for splitting: " + mtry);
            }

            this.mtry = mtry;
            return this;
        }

        /**
         * Sets the maximum number of leaf nodes.
         * @param maxNodes the maximum number of leaf nodes.
         */
        public Trainer setMaxNodes(int maxNodes) {
            if (maxNodes < 2) {
                throw new IllegalArgumentException("Invalid minimum size of leaf nodes: " + maxNodes);
            }

            this.maxNodes = maxNodes;
            return this;
        }

        /**
         * Sets the minimum size of leaf nodes.
         * @param nodeSize the number of instances in a node below which the tree will not split.
         */
        public Trainer setNodeSize(int nodeSize) {
            if (nodeSize < 1) {
                throw new IllegalArgumentException("Invalid minimum size of leaf nodes: " + nodeSize);
            }

            this.nodeSize = nodeSize;
            return this;
        }

        /**
         * Sets the sampling rate.
         * @param subsample the sampling rate.
         */
        public Trainer setSamplingRates(double subsample) {
            if (subsample <= 0 || subsample > 1) {
                throw new IllegalArgumentException("Invalid sampling rating: " + subsample);
            }

            this.subsample = subsample;
            return this;
        }

        @Override
        public RandomForest train(double[][] x, int[] y) {
            return new RandomForest(attributes, x, y, ntrees, maxNodes, nodeSize, mtry, subsample, rule, null);
        }
    }
    
    /**
     * Trains a regression tree.
     */
    static class TrainingTask implements Callable<Tree>, Serializable {

        /**
         * Dataset
         */
        AttributeDataset dataset;
        /**
         * The number of variables to pick up in each node.
         */
        int mtry;
        /**
         * The minimum size of leaf nodes.
         */
        int nodeSize = 5;
        /**
         * The maximum number of leaf nodes in the tree.
         */
        int maxNodes = 100;
        /**
         * The sampling rate.
         */
        double subsample = 1.0;
        /**
         * The splitting rule.
         */
        DecisionTree.SplitRule rule;
        /**
         * Priors of the classes.
         */
        int[] classWeight;
        /**
         * The index of training values in ascending order. Note that only
         * numeric attributes will be sorted.
         */
        AtomicMatrix<Integer> order;
        /**
         * The out-of-bag predictions.
         */
        AtomicMatrix<Integer> prediction;

        DecisionTree tree;

        /**
         * Constructor.
         */
        TrainingTask(AttributeDataset dataset, int maxNodes, int nodeSize, int mtry, double subsample, DecisionTree.SplitRule rule, int[] classWeight, AtomicMatrix<Integer> order, AtomicMatrix<Integer> prediction) {
            this.dataset = dataset;
            this.mtry = mtry;
            this.nodeSize = nodeSize;
            this.maxNodes = maxNodes;
            this.subsample = subsample;
            this.rule = rule;
            this.classWeight = classWeight;
            this.order = order;
            this.prediction = prediction;
        }

        @Override
        public Tree call() {
            double[][] data = dataset.x();
            int[] y = dataset.labels();
            Attribute[] attributes = dataset.attributes();
            int n = data.length;
            int k = smile.math.Math.max(y) + 1;
            int[] samples = new int[n];
            // Stratified sampling in case class is unbalanced.
            // That is, we sample each class separately.
            if (subsample == 1.0) {
                // Training samples draw with replacement.
                for (int l = 0; l < k; l++) {
                    int nj = 0;
                    ArrayList<Integer> cj = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        if (y[i] == l) {
                            cj.add(i);
                            nj++;
                        }
                    }

                    // We used to do up sampling.
                    // But we switch to down sampling, which seems has better performance.
                    int size = nj / classWeight[l];
                    for (int i = 0; i < size; i++) {
                        int xi = Math.randomInt(nj);
                        samples[cj.get(xi)] += 1; //classWeight[l];
                    }
                }
            } else {
                // Training samples draw without replacement.
                int[] perm = new int[n];
                for (int i = 0; i < n; i++) {
                    perm[i] = i;
                }

                Math.permutate(perm);

                int[] nc = new int[k];
                for (int i = 0; i < n; i++) {
                    nc[y[i]]++;
                }

                for (int l = 0; l < k; l++) {
                    int subj = (int) Math.round(nc[l] * subsample / classWeight[l]);
                    int count = 0;
                    for (int i = 0; i < n && count < subj; i++) {
                        int xi = perm[i];
                        if (y[xi] == l) {
                            samples[xi] += 1; //classWeight[l];
                            count++;
                        }
                    }
                }
            }
            // samples will be changed during tree construction.
            // make a copy so that we can estimate oob error correctly.
            tree = new DecisionTree(attributes, data, y, maxNodes, nodeSize, mtry, rule, samples.clone(), Stream.unboxInteger2D(order.toArray()));

            // estimate OOB error
            int row = prediction.rows();
            int column = prediction.columns();
            int oob = 0;
            int correct = 0;
            Integer[][] toAdd = new Integer[column][row];
            for (int i = 0; i < column; i++) {
                for (int j = 0; j < row; j++) {
                    toAdd[i][j] = new Integer(0);
                }
            }
            for (int i = 0; i < n; i++) {
                if (samples[i] == 0) {
                    oob++;
                    int p = tree.predict(data[i]);
                    if (p == y[i]) correct++;
                    toAdd[i][p] = toAdd[i][p] + toAdd[i][p]+1;
                }
            }

            prediction.compute(toAdd,((org.infinispan.creson.RemoteBiFunction<Integer,Integer,Integer>) Integer::sum));
            double accuracy = 1.0;
            if (oob != 0) {
                accuracy = (double) correct / oob;
                logger.info("Random forest tree OOB size: {}, accuracy: {}", oob, String.format("%.2f%%", 100 * accuracy));
            } else {
                logger.error("Random forest has a tree trained without OOB samples.");
            }

            return new Tree(tree, accuracy);
        }
    }

    /**
     * Constructor. Learns a random forest for classification.
     *
     * @param x the training instances. 
     * @param y the response variable.
     * @param ntrees the number of trees.
     */
    public RandomForest(double[][] x, int[] y, int ntrees) {
        this(null, x, y, ntrees);
    }

    /**
     * Constructor. Learns a random forest for classification.
     *
     * @param x the training instances. 
     * @param y the response variable.
     * @param ntrees the number of trees.
     * @param mtry the number of random selected features to be used to determine
     * the decision at a node of the tree. floor(sqrt(dim)) seems to give
     * generally good performance, where dim is the number of variables.
     */
    public RandomForest(double[][] x, int[] y, int ntrees, int mtry) {
        this(null, x, y, ntrees, mtry);
    }

    /**
     * Constructor. Learns a random forest for classification.
     *
     * @param attributes the attribute properties.
     * @param x the training instances. 
     * @param y the response variable.
     * @param ntrees the number of trees.
     */
    public RandomForest(Attribute[] attributes, double[][] x, int[] y, int ntrees) {
        this(attributes, x, y, ntrees, (int) Math.floor(Math.sqrt(x[0].length)));
    }

    /**
     * Constructor. Learns a random forest for classification.
     *
     * @param attributes the attribute properties.
     * @param x the training instances.
     * @param y the response variable.
     * @param ntrees the number of trees.
     * @param mtry the number of random selected features to be used to determine
     * the decision at a node of the tree. floor(sqrt(dim)) seems to give
     * generally good performance, where dim is the number of variables.
     */
    public RandomForest(Attribute[] attributes, double[][] x, int[] y, int ntrees, int mtry) {
        this(attributes, x, y, ntrees, 100, 5, mtry, 1.0);

    }

    /**
     * Constructor. Learns a random forest for classification.
     *
     * @param data the dataset
     * @param ntrees the number of trees.
     * @param mtry the number of random selected features to be used to determine
     * the decision at a node of the tree. floor(sqrt(dim)) seems to give
     * generally good performance, where dim is the number of variables.
     */
    public RandomForest(AttributeDataset data, int ntrees, int mtry) {
        this(data.attributes(), data.x(), data.labels(), ntrees, mtry);
    }

    /**
     * Constructor. Learns a random forest for classification.
     *
     * @param attributes the attribute properties.
     * @param x the training instances.
     * @param y the response variable.
     * @param ntrees the number of trees.
     * @param mtry the number of random selected features to be used to determine
     * the decision at a node of the tree. floor(sqrt(dim)) seems to give
     * generally good performance, where dim is the number of variables.
     * @param nodeSize the minimum size of leaf nodes.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param subsample the sampling rate for training tree. 1.0 means sampling with replacement. < 1.0 means
     *                  sampling without replacement.
     */
    public RandomForest(Attribute[] attributes, double[][] x, int[] y, int ntrees, int maxNodes, int nodeSize, int mtry, double subsample) {
        this(attributes, x, y, ntrees, maxNodes, nodeSize, mtry, subsample, DecisionTree.SplitRule.GINI);
    }

    /**
     * Constructor. Learns a random forest for classification.
     *
     * @param attributes the attribute properties.
     * @param x the training instances.
     * @param y the response variable.
     * @param ntrees the number of trees.
     * @param mtry the number of random selected features to be used to determine
     * the decision at a node of the tree. floor(sqrt(dim)) seems to give
     * generally good performance, where dim is the number of variables.
     * @param nodeSize the minimum size of leaf nodes.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param subsample the sampling rate for training tree. 1.0 means sampling with replacement. < 1.0 means
     *                  sampling without replacement.
     * @param rule Decision tree split rule.
     */
    public RandomForest(Attribute[] attributes, double[][] x, int[] y, int ntrees, int maxNodes, int nodeSize, int mtry, double subsample, DecisionTree.SplitRule rule) {
        this(attributes, x, y, ntrees, maxNodes, nodeSize, mtry, subsample, rule, null);
    }

    /**
     * Constructor. Learns a random forest for classification.
     *
     * @param attributes the attribute properties.
     * @param x the training instances.
     * @param y the response variable.
     * @param ntrees the number of trees.
     * @param mtry the number of random selected features to be used to determine
     * the decision at a node of the tree. floor(sqrt(dim)) seems to give
     * generally good performance, where dim is the number of variables.
     * @param nodeSize the minimum size of leaf nodes.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param subsample the sampling rate for training tree. 1.0 means sampling with replacement. < 1.0 means
     *                  sampling without replacement.
     * @param rule Decision tree split rule.
     * @param classWeight Priors of the classes. The weight of each class
     *                    is roughly the ratio of samples in each class.
     *                    For example, if
     *                    there are 400 positive samples and 100 negative
     *                    samples, the classWeight should be [1, 4]
     *                    (assuming label 0 is of negative, label 1 is of
     *                    positive).
     */
    public RandomForest(Attribute[] attributes, double[][] x, int[] y, int ntrees, int maxNodes, int nodeSize, int mtry, double subsample, DecisionTree.SplitRule rule, int[] classWeight) {
        this(new AttributeDataset("dataset",x,Arrays.stream(y).asDoubleStream().toArray()), y, ntrees, maxNodes, nodeSize, mtry, subsample, rule);
    }

    /**
     * Constructor. Learns a random forest for classification.
     *
     * @param dataset the dataset
     * @param y the response variable.
     * @param ntrees the number of trees.
     * @param mtry the number of random selected features to be used to determine
     * the decision at a node of the tree. floor(sqrt(dim)) seems to give
     * generally good performance, where dim is the number of variables.
     * @param nodeSize the minimum size of leaf nodes.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param subsample the sampling rate for training tree. 1.0 means sampling with replacement. < 1.0 means
     *                  sampling without replacement.
     * @param rule Decision tree split rule.
     */
    public RandomForest(AttributeDataset dataset, int[] y, int ntrees, int maxNodes, int nodeSize, int mtry, double subsample, DecisionTree.SplitRule rule) {
        this(dataset, dataset.attributes(), y, ntrees, maxNodes, nodeSize, mtry, subsample, rule,null);
    }

    /**
     * Constructor. Learns a random forest for classification.
     *
     * @param data the dataset
     * @param ntrees the number of trees.
     * generally good performance, where dim is the number of variables.
     */
    public RandomForest(AttributeDataset data, int ntrees) {
        this(data, data.attributes(), data.labels(), ntrees, 100, 5, (int) Math.floor(Math.sqrt(data.x()[0].length)), 1.0, DecisionTree.SplitRule.GINI,null);
    }

    /**
     * Constructor. Learns a random forest for classification.
     *
     * @param dataset the dataset.
     * @param y the response variable.
     * @param ntrees the number of trees.
     * @param mtry the number of random selected features to be used to determine
     * the decision at a node of the tree. floor(sqrt(dim)) seems to give
     * generally good performance, where dim is the number of variables.
     * @param nodeSize the minimum size of leaf nodes.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param subsample the sampling rate for training tree. 1.0 means sampling with replacement. < 1.0 means
     *                  sampling without replacement.
     * @param rule Decision tree split rule.
     * @param classWeight Priors of the classes. The weight of each class
     *                    is roughly the ratio of samples in each class.
     *                    For example, if
     *                    there are 400 positive samples and 100 negative
     *                    samples, the classWeight should be [1, 4]
     *                    (assuming label 0 is of negative, label 1 is of
     *                    positive).
     */
    public RandomForest(AttributeDataset dataset, Attribute[] attributes, int[] y, int ntrees, int maxNodes, int nodeSize, int mtry, double subsample, DecisionTree.SplitRule rule, int[] classWeight) {
        if (ntrees < 1) {
            throw new IllegalArgumentException("Invalid number of trees: " + ntrees);
        }

        if (mtry < 1 || mtry >  attributes.length) {
            throw new IllegalArgumentException("Invalid number of variables to split on at a node of the tree: " + mtry);
        }

        if (nodeSize < 1) {
            throw new IllegalArgumentException("Invalid minimum size of leaves: " + nodeSize);
        }

        if (maxNodes < 2) {
            throw new IllegalArgumentException("Invalid maximum number of leaves: " + maxNodes);
        }

        if (subsample <= 0 || subsample > 1) {
            throw new IllegalArgumentException("Invalid sampling rating: " + subsample);
        }

        // class label set.
        int[] labels = Math.unique(y);
        Arrays.sort(labels);

        for (int i = 0; i < labels.length; i++) {
            if (labels[i] < 0) {
                throw new IllegalArgumentException("Negative class label: " + labels[i]);
            }

            if (i > 0 && labels[i] - labels[i-1] > 1) {
                throw new IllegalArgumentException("Missing class: " + (labels[i-1]+1));
            }
        }

        k = labels.length;
        if (k < 2) {
            throw new IllegalArgumentException("Only one class.");
        }

        if (classWeight == null) {
            classWeight = new int[k];
            for (int i = 0; i < k; i++) classWeight[i] = 1;
        }

        int n = dataset.size();
        AtomicMatrix<Integer> prediction = null; // out-of-bag prediction
        AtomicMatrix<Integer> order = null;

//        prediction = Factory.getSingleton().getInstanceOf(AtomicMatrix.class,"prediction",false, false, true, "prediction", Integer.class, dataset.size(), k);
//        order = Factory.getSingleton().getInstanceOf(AtomicMatrix.class,"order",false, false, true, "order", Stream.boxInteger2D(SmileUtils.sort(attributes, dataset.x())));

        prediction = new AtomicMatrix<>("prediction", Integer.class, n, k);
        order = new AtomicMatrix("order",Stream.boxInteger2D(SmileUtils.sort(attributes, dataset.x())));

        prediction.forEach(new LConstructor());

        List<TrainingTask> tasks = new ArrayList<>();
        for (int i = 0; i < ntrees; i++) {
            tasks.add(new TrainingTask(dataset, maxNodes, nodeSize, mtry, subsample, rule, classWeight, order, prediction)); // FIXME
        }

        try {
//            trees = ServerlessExecutor.run(tasks);
            trees = MulticoreExecutor.run(tasks);
        } catch (Exception ex) {
            logger.error("Failed to train random forest", ex);

            trees = new ArrayList<>(ntrees);
            for (int i = 0; i < ntrees; i++) {
                trees.add(tasks.get(i).call());
            }
        }

        int[][] pred_array = Stream.unboxInteger2D(prediction.toArray());
        int m = 0;
        for (int i = 0; i < n; i++) {
            int pred = 0; Math.whichMax(pred_array[i]);
            if (pred_array[i][pred] > 0) {
                m++;
                if (pred != y[i]) {
                    error++;
                }
            }
        }

        if (m > 0) {
            error /= m;
        }

        importance = new double[attributes.length];
        for (Tree tree : trees) {
            double[] imp = tree.tree.importance();
            for (int i = 0; i < imp.length; i++) {
                importance[i] += imp[i];
            }
        }
    }

    public static class LConstructor implements Function, Serializable{

        @Override
        public Object apply(Object o) {
            return new Integer(0);
        }
    }



    public static class LSum implements BiFunction<Integer,Integer,Integer>, Serializable{
        @Override
        public Integer apply(Integer integer, Integer integer2) {
            return Integer.sum(integer,integer2);
        }
    }

    /**
     * Returns the out-of-bag estimation of error rate. The OOB estimate is
     * quite accurate given that enough trees have been grown. Otherwise the
     * OOB estimate can bias upward.
     * 
     * @return the out-of-bag estimation of error rate
     */
    public double error() {
        return error;
    }
    
    /**
     * Returns the variable importance. Every time a split of a node is made
     * on variable the (GINI, information gain, etc.) impurity criterion for
     * the two descendent nodes is less than the parent node. Adding up the
     * decreases for each individual variable over all trees in the forest
     * gives a fast measure of variable importance that is often very
     * consistent with the permutation importance measure.
     *
     * @return the variable importance
     */
    public double[] importance() {
        return importance;
    }
    
    /**
     * Returns the number of trees in the model.
     * 
     * @return the number of trees in the model 
     */
    public int size() {
        return trees.size();
    }
    
    /**
     * Trims the tree model set to a smaller size in case of over-fitting.
     * Or if extra decision trees in the model don't improve the performance,
     * we may remove them to reduce the model size and also improve the speed of
     * prediction.
     * 
     * @param ntrees the new (smaller) size of tree model set.
     */
    public void trim(int ntrees) {
        if (ntrees > trees.size()) {
            throw new IllegalArgumentException("The new model size is larger than the current size.");
        }
        
        if (ntrees <= 0) {
            throw new IllegalArgumentException("Invalid new model size: " + ntrees);
        }

        List<Tree> model = new ArrayList<>(ntrees);
        for (int i = 0; i < ntrees; i++) {
            model.add(trees.get(i));
        }
        
        trees = model;
    }
    
    @Override
    public int predict(double[] x) {
        int[] y = new int[k];
        
        for (Tree tree : trees) {
            y[tree.tree.predict(x)]++;
        }
        
        return Math.whichMax(y);
    }
    
    @Override
    public int predict(double[] x, double[] posteriori) {
        if (posteriori.length != k) {
            throw new IllegalArgumentException(String.format("Invalid posteriori vector size: %d, expected: %d", posteriori.length, k));
        }

        Arrays.fill(posteriori, 0.0);

        int[] y = new int[k];
        double[] pos = new double[k];
        for (Tree tree : trees) {
            y[tree.tree.predict(x, pos)]++;
            for (int i = 0; i < k; i++) {
                posteriori[i] += tree.weight * pos[i];
            }
        }

        Math.unitize1(posteriori);
        return Math.whichMax(y);
    }    
    
    /**
     * Test the model on a validation dataset.
     * 
     * @param x the test data set.
     * @param y the test data response values.
     * @return accuracies with first 1, 2, ..., decision trees.
     */
    public double[] test(double[][] x, int[] y) {
        int T = trees.size();
        double[] accuracy = new double[T];

        int n = x.length;
        int[] label = new int[n];
        int[][] prediction = new int[n][k];

        Accuracy measure = new Accuracy();
        
        for (int i = 0; i < T; i++) {
            for (int j = 0; j < n; j++) {
                prediction[j][trees.get(i).tree.predict(x[j])]++;
                label[j] = Math.whichMax(prediction[j]);
            }

            accuracy[i] = measure.measure(y, label);
        }

        return accuracy;
    }
    
    /**
     * Test the model on a validation dataset.
     * 
     * @param x the test data set.
     * @param y the test data labels.
     * @param measures the performance measures of classification.
     * @return performance measures with first 1, 2, ..., decision trees.
     */
    public double[][] test(double[][] x, int[] y, ClassificationMeasure[] measures) {
        int T = trees.size();
        int m = measures.length;
        double[][] results = new double[T][m];

        int n = x.length;
        int[] label = new int[n];
        double[][] prediction = new double[n][k];

        for (int i = 0; i < T; i++) {
            for (int j = 0; j < n; j++) {
                prediction[j][trees.get(i).tree.predict(x[j])]++;
                label[j] = Math.whichMax(prediction[j]);
            }

            for (int j = 0; j < m; j++) {
                results[i][j] = measures[j].measure(y, label);
            }
        }
        return results;
    }

    /**
     * Returns the decision trees.
     */
    public DecisionTree[] getTrees() {
        DecisionTree[] forest = new DecisionTree[trees.size()];
        for (int i = 0; i < forest.length; i++)
            forest[i] = trees.get(i).tree;

        return forest;
    }
}
