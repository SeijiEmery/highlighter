package highlighter;

/**
 * Created by Seiji on 4/12/15.
 */
public interface Stats {
    public void beginParserInit ();
    public void endParserInit ();
    public void beginTrieInit ();
    public void endTrieInit ();
    public void beginTrieMatch ();
    public void endTrieMatch ();
    public void beginParse ();
    public void endParse ();
    public void beginHtmlGen ();
    public void endHtmlGen ();
    public void beginFileRead ();
    public void endFileRead ();
    public void beginFileWrite ();
    public void endFileWrite ();
    public void beginProcessingFile ();
    public void endProcessingFile ();
    public void beginProcessingDir ();
    public void endProcessingDir ();
    public void startHtmlify ();
    public void endHtmlify ();

    public String getStats ();
    public String getAdjustedStats ();
}
