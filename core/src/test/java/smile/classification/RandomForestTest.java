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

import org.crucial.dso.Factory;
import org.crucial.dso.client.Client;
import org.crucial.executor.aws.AWSLambdaExecutorService;
import org.junit.*;
import smile.data.Attribute;
import smile.data.AttributeDataset;
import smile.data.LazyS3AttributeDataset;
import smile.data.NominalAttribute;
import smile.data.parser.ArffParser;
import smile.data.parser.DelimitedTextParser;
import smile.math.Math;
import smile.sort.QuickSort;
import smile.util.ServerlessExecutor;
import smile.validation.LOOCV;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.Assert.assertTrue;

/**
 *
 * @author Haifeng
 */
public class RandomForestTest {

    private Properties properties;

    public RandomForestTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {}

    @AfterClass
    public static void tearDownClass() throws Exception {}
    
    @Before
    public void setUp() {
        properties = System.getProperties();
        try (InputStream is = this.getClass().getClassLoader().getResourceAsStream("config.properties")) {
            properties.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        }
        ServerlessExecutor.createThreadPool(new AWSLambdaExecutorService(properties));
        Client.getClient().clear();
    }
    
    @After
    public void tearDown() {
    }

    /**
     * Test of learn method, of class RandomForest.
     */
    // @Test
    public void testWeather() {
        System.out.println("Weather");
        ArffParser arffParser = new ArffParser();
        arffParser.setResponseIndex(4);
        try {
            // AttributeDataset weather = arffParser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/weather.nominal.arff"));
            // weather = new LazyAttributeDataSet(ArffParser,smile.data.parser.IOUtils.getS3File("cloudbutton","weather.nominal.arff"));
            AttributeDataset weather = new LazyS3AttributeDataset("weather","eu-west-3","cloudbutton","weather.nominal.arff",4);
            double[][] x = weather.toArray(new double[weather.size()][]);
            int[] y = weather.toArray(new int[weather.size()]);

            // int n = x.length;
            int n = x.length;
            LOOCV loocv = new LOOCV(n);
            int error = 0;
            for (int i = 0; i < n; i++) {
                double[][] trainx = Math.slice(x, loocv.train[i]);
                int[] trainy = Math.slice(y, loocv.train[i]);

                RandomForest forest = new RandomForest(weather, 50);
                if (y[loocv.test[i]] != forest.predict(x[loocv.test[i]]))
                    error++;
                break;
            }
            
            System.out.println("Random Forest error = " + error);
            assertTrue(error <= 7);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Test of learn method, of class RandomForest.
     */
    // @Test
    public void testIris() {
        System.out.println("Iris");
        try {
            // AttributeDataset iris = arffParser.parse(smile.data.parser.IOUtils.getTestDataFile("weka/weather.nominal.arff"));
            AttributeDataset iris = new LazyS3AttributeDataset("iris","eu-west-3","cloudbutton","iris.arff",4);
            ArffParser arffParser = new ArffParser();
            arffParser.setResponseIndex(4);
            double[][] x = iris.toArray(new double[iris.size()][]);
            int[] y = iris.toArray(new int[iris.size()]);

            int n = x.length;
            LOOCV loocv = new LOOCV(n);
            int error = 0;
            for (int i = 0; i < n; i++) {
//                double[][] trainx = Math.slice(x, loocv.train[i]);
//                int[] trainy = Math.slice(y, loocv.train[i]);
                RandomForest forest = new RandomForest(iris, 50);
                if (y[loocv.test[i]] != forest.predict(x[loocv.test[i]]))
                    error++;
                break;
            }
            
            System.out.println("Random Forest error = " + error);
            assertTrue(error <= 9);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of learn method, of class RandomForest.
     */
    // @Test
    public void testUSPS() {
        System.out.println("USPS");
        DelimitedTextParser parser = new DelimitedTextParser();
        parser.setResponseIndex(new NominalAttribute("class"), 0);
        parser.setDelimiter(" ");
        try {

            int ntrees = 10;
            // AttributeDataset train = parser.parse("USPS Train", smile.data.parser.IOUtils.getTestDataFile("usps/zip.train"));
            AttributeDataset train = new LazyS3AttributeDataset("USPS Train", "eu-west-3","cloudbutton","zip.train",0);
            System.out.println("loaded: "+train.size());

            double[][] testx = train.toArray(new double[train.size()][]);
            int[] testy = train.toArray(new int[train.size()]);

            RandomForest forest = new RandomForest(train, ntrees, 100, 5, (int) Math.floor(Math.sqrt(train.x()[0].length)), 1.0, DecisionTree.SplitRule.GINI,null);

            // AttributeDataset string = parser.parse("USPS Test", smile.data.parser.IOUtils.getTestDataFile("usps/zip.test"));
            AttributeDataset line = new LazyS3AttributeDataset("USPS Test", "eu-west-3","cloudbutton","zip.test",0);

            int error = 0;
            for (int i = 0; i < testx.length; i++) {
                if (forest.predict(testx[i]) != testy[i]) {
                    error++;
                }
            }

            System.out.println("USPS error = " + error);
            System.out.format("USPS OOB error rate = %.2f%%%n", 100.0 * forest.error());
            System.out.format("USPS error rate = %.2f%%%n", 100.0 * error / testx.length);
            assertTrue(error <= 225);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Test of learn method, of class RandomForest.
     */
    // @Test
    public void testUSPSNominal() {
        System.out.println("USPS nominal");
        DelimitedTextParser parser = new DelimitedTextParser();
        parser.setResponseIndex(new NominalAttribute("class"), 0);
        try {
            AttributeDataset train = parser.parse("USPS Train", smile.data.parser.IOUtils.getTestDataFile("usps/zip.train"));
            AttributeDataset test = parser.parse("USPS Test", smile.data.parser.IOUtils.getTestDataFile("usps/zip.test"));

            double[][] x = train.toArray(new double[train.size()][]);
            int[] y = train.toArray(new int[train.size()]);
            double[][] testx = test.toArray(new double[test.size()][]);
            int[] testy = test.toArray(new int[test.size()]);
            
            for (double[] xi : x) {
                for (int i = 0; i < xi.length; i++) {
                    xi[i] = Math.round(255*(xi[i]+1)/2);
                }
            }
            
            for (double[] xi : testx) {
                for (int i = 0; i < xi.length; i++) {
                    xi[i] = Math.round(255*(xi[i]+1)/2);
                }
            }
            
            Attribute[] attributes = new Attribute[256];
            String[] values = new String[attributes.length];
            for (int i = 0; i < attributes.length; i++) {
                values[i] = String.valueOf(i);
            }
            
            for (int i = 0; i < attributes.length; i++) {
                attributes[i] = new NominalAttribute("V"+i, values);
            }
            
            RandomForest forest = new RandomForest(attributes, x, y, 50);
            
            int error = 0;
            for (int i = 0; i < testx.length; i++) {
                if (forest.predict(testx[i]) != testy[i]) {
                    error++;
                }
            }

            System.out.println(error);
            System.out.format("USPS OOB error rate = %.2f%%%n", 100.0 * forest.error());
            System.out.format("USPS error rate = %.2f%%%n", 100.0 * error / testx.length);
            
            double[] accuracy = forest.test(testx, testy);
            for (int i = 1; i <= accuracy.length; i++) {
                System.out.format("%d trees accuracy = %.2f%%%n", i, 100.0 * accuracy[i-1]);
            }
            
            double[] importance = forest.importance();
            int[] index = QuickSort.sort(importance);
            for (int i = importance.length; i-- > 0; ) {
                System.out.format("%s importance is %.4f%n", train.attributes()[index[i]], importance[i]);
            }

            System.out.println("USPS Nominal error = " + error);
            System.out.format("USPS Nominal OOB error rate = %.2f%%%n", 100.0 * forest.error());
            System.out.format("USPS Nominal error rate = %.2f%%%n", 100.0 * error / testx.length);
            assertTrue(error <= 250);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    private void validate(int trees, String dataset, String url, int response) {
        long time = System.currentTimeMillis();
        ArffParser parser = new ArffParser();
        parser.setResponseIndex(response);
        try {
            AttributeDataset data = new LazyS3AttributeDataset(dataset,"eu-west-2","cloudbutton-dataset",url, response);
//            int[] datay = data.toArray(new int[data.size()]);
//            double[][] datax = data.toArray(new double[data.size()][]);

//            int n = datax.length;
//            int k = 2;
//
//            CrossValidation cv = new CrossValidation(n, k);
//            double area = 0;
//            double sa = 0;
//            for (int i = 0; i < k; i++) {
//                double[][] trainx = Math.slice(datax, cv.train[i]);
//                int[] trainy = Math.slice(datay, cv.train[i]);
//                double[][] testx = Math.slice(datax, cv.test[i]);
//                int[] testy = Math.slice(datay, cv.test[i]);

            RandomForest forest = new RandomForest(data,trees);
            System.out.format("OOB error rate = %.4f%n", forest.error());

//                int[] truth = testy;
//                double[] probability = new double[testx.length];
//
//                for (int j = 0; j < testx.length; j++) {
//                    double[] posteriori = {0.0,0.0};
//                    forest.predict(testx[j],posteriori);
//                    probability[j] = posteriori[1];
//                }
//                area=AUC.measure(truth,probability);
//                System.out.println("AUC="+area);
//                sa+=area;
//            }

//            time = System.currentTimeMillis() - time;
//            System.out.format(dataset+" "+trees+" "+k+"-CV avg AUC="+(sa/k)+" (in "+ time + "ms)");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // @Test
    public void testClick() {
//        test(1,"iris", "weka/iris.arff", 4);
//        test(10,"iris", "weka/iris.arff", 4);
//        test(100,"iris", "weka/iris.arff", 4);
//        test(1000,"iris", "weka/iris.arff", 4);

//        test(1,"click", "click/train.arff", 0);
//        test(10,"click", "click/train.arff", 0);
//        test(100,"click", "click/train.arff", 0);
//        test(1000,"click", "click/train.arff", 0);

//        test(1,"credit-g", "credit-g/train.arff", 20);
//        test(100,"credit-g", "credit-g/train.arff", 20);
//        test(1000,"credit-g", "credit-g/train.arff", 20);

//        test(1,"soil", "soil/train.arff", 36);
//        test(10,"soil", "soil/train.arff", 36);
//        test(100,"soil", "soil/train.arff", 36);

        test(50,"weather.nominal", 4);

        // test(10,"soil", 36);
        // test(100,"click", "click.arff", 0);
        // test(100,"weather", "weather.nominal.arff", 4);

//        test(1,"kdd",  41);
//        test(10,"creditcard", "creditcard.arff", 30);

    }


    private void test(int trees, String dataset, int response) {
        long time = System.currentTimeMillis();
        String url = dataset+".arff";
        ArffParser parser = new ArffParser();
        parser.setResponseIndex(response);
        try {
            AttributeDataset data = new LazyS3AttributeDataset(dataset,"eu-west-2","cloudbutton-dataset",url, response);
            RandomForest forest = new RandomForest(data,trees);
            // System.err.format("OOB error rate = %.4f%n", forest.error());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Test
    public void benchmark(){
        String[] datasets = {"soil", "usps"};
        Integer[] response = {36,0};

        int ntrees = 10;

        for (int i=0; i<datasets.length; i++) {
            long start = System.currentTimeMillis();
            test(ntrees, datasets[i], response[i]);
            System.out.println(datasets[i]+"\t"+(System.currentTimeMillis()-start));
        }
    }

    // @Test
    public void benchmark2(){
        Integer[] sizes = {200};
        for (int i=0; i<sizes.length; i++) {
            System.out.print(sizes[i]+"\t");
            for(int k= 0; k<10; k++) {
                long start = System.currentTimeMillis();
                test(sizes[i], "creditcard", 30);
                System.out.print((System.currentTimeMillis() - start)+"\t");
            }
            System.out.println("");
        }
    }


}
