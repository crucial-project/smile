package smile.data;

import smile.data.parser.ArffParser;
import smile.data.parser.DelimitedTextParser;

import java.io.*;
import java.text.ParseException;
import java.util.Arrays;

public class LazyS3AttributeDataset extends AttributeDataset implements Serializable {

    private String region, bucket, file;
    transient private AttributeDataset delegate;

    public LazyS3AttributeDataset(String name, String region, String bucket, String file) {
        super(name, null);
        this.region = region;
        this.bucket = bucket;
        this.file = file;
    }

    private void create(){
        if (delegate==null){
            if (file.contains("arf")) {
                ArffParser parser = new ArffParser(); // FIXME
                parser.setResponseIndex(4);
                try {
                    delegate = parser.parse(smile.data.parser.IOUtils.getS3File(region,bucket,file));
                } catch (IOException | ParseException e) {
                    e.printStackTrace();
                }
            } else {
                // FIXME
                DelimitedTextParser parser = new DelimitedTextParser();
                // parser.setResponseIndex(new NominalAttribute("class"), 0);
                parser.setResponseIndex(new NominalAttribute("dAge"), 1);
                parser.setDelimiter(" ");
                try {
                    delegate = parser.parse(name, smile.data.parser.IOUtils.getS3File(region,bucket,file));
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }

        }

    }

    @Override
    public int[] labels() {
        create();
        return  delegate.labels();
    }

    @Override
    public int size(){
        create();
        return delegate.size();
    }

    @Override
    public double[][] x(){
        create();
        return  delegate.x();
    }

    @Override
    public Attribute[] attributes(){
        create();
        return  delegate.attributes();
    }

    @Override
    public double[][] toArray(double[][] x){
        create();
        return  delegate.toArray(x);
    }

    @Override
    public double[] toArray(double[] x){
        create();
        return  delegate.toArray(x);
    }

    @Override
    public int[] toArray(int[] x){
        create();
        return  delegate.toArray(x);
    }

    @Override
    public String toString(){
        create();
        return delegate.toString();
    }

}
