package highlighter;

/**
 * Created by Seiji on 4/12/15.
 */
public class FastStats implements Stats {
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
    public void beginProcessingFile() {}

    @Override
    public void endProcessingFile() {}

    @Override
    public void beginProcessingDir() {}

    @Override
    public void endProcessingDir() {}

    private long runStart = 0;
    private long runTime  = 0;

    @Override
    public void startHtmlify() {
        runStart = System.nanoTime();
    }

    @Override
    public void endHtmlify() {
        runTime = System.nanoTime() - runStart;
    }

    @Override
    public String getStats() {
        return String.format("Finished in %f ms", (double)runTime * 1e-6);
    }

    @Override
    public String getAdjustedStats() {
        return getStats();
    }
}
