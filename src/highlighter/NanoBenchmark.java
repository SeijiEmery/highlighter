package highlighter;

/**
 * Created by Seiji on 4/12/15.
 */
public class NanoBenchmark {
    public static void main (String[] args) {
        long t = 0, lt = 0;


        long t0 = System.nanoTime();
        for (int i = 0; i < 1e6; ++i) {
            long ct = System.nanoTime();
        }
        long elapsed = System.nanoTime() - t0;

        System.out.printf("%f\n%f\n", (float)elapsed * 1e-6, (float)t * 1e-6);

    }
}
