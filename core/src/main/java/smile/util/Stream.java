package smile.util;

import java.util.Arrays;

public class Stream {


    // (un)boxing

    public static Double[][] boxDouble2D(double[][] unboxed) {
        return unboxed == null ? null : Arrays.stream(unboxed).map(Stream::boxDouble1D).toArray(Double[][]::new);
    }

    public static double[][] unboxDouble2D(Double[][] boxed) {
        return boxed == null ? null : Arrays.stream(boxed).map(Stream::unboxDouble1D).toArray(double[][]::new);
    }

    public static Integer[][] boxInteger2D(int[][] unboxed) {
        return unboxed == null ? null : Arrays.stream(unboxed).map(Stream::boxInteger1D).toArray(Integer[][]::new);
    }

    public static int[][] unboxInteger2D(Integer[][] boxed) {
        return boxed == null ? null : Arrays.stream(boxed).map(Stream::unboxInteger1D).toArray(int[][]::new);
    }

    public static Integer[] boxInteger1D(int[] unboxed) {
        return unboxed == null ? null : Arrays.stream(unboxed).boxed().toArray(Integer[]::new);
    }

    public static int[] unboxInteger1D(Integer[] boxed) {
        return boxed == null ? null : Arrays.stream(boxed).mapToInt(Integer::intValue).toArray();
    }

    public static Double[] boxDouble1D(double[] unboxed) {
        return unboxed == null ? null : Arrays.stream(unboxed).boxed().toArray(Double[]::new);
    }

    public static double[] unboxDouble1D(Double[] boxed) {
        return boxed == null ? null : Arrays.stream(boxed).mapToDouble(Double::doubleValue).toArray();
    }




}
