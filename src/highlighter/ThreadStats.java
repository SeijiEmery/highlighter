package highlighter;

/**
 * Created by Seiji on 4/12/15.
 */
public class ThreadStats implements Stats {
    @Override
    public void beginParserInit() {}

    @Override
    public void endParserInit() {}

    @Override
    public void beginTrieInit() {}

    @Override
    public void endTrieInit() {}

    @Override
    public void beginTrieMatch() {}

    @Override
    public void endTrieMatch() {}

    @Override
    public void beginParse() {}

    @Override
    public void endParse() {}

    @Override
    public void beginHtmlGen() {}

    @Override
    public void endHtmlGen() {}

    @Override
    public void beginFileRead() {}

    @Override
    public void endFileRead() {}

    @Override
    public void beginFileWrite() {}

    @Override
    public void endFileWrite() {}

    @Override
    public void beginProcessingDir() {}

    @Override
    public void endProcessingDir() {}

    @Override
    public void startHtmlify() {}

    @Override
    public void endHtmlify() {}

    private long startTime = 0;
    private long activeTime = 0;
    private int numCalls;

    @Override
    public void beginProcessingFile() {
        startTime = System.nanoTime();
    }

    @Override
    public void endProcessingFile() {
        activeTime += System.nanoTime() - startTime;
        ++numCalls;
    }

    @Override
    public String getStats() {
        return String.format("Active time: %f ms across %d calls", (double)activeTime * 1e-6, numCalls);
    }

    @Override
    public String getAdjustedStats() {
        return getStats();
    }
}
