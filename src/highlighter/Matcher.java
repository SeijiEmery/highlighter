package highlighter;

/**
 * Created by Seiji on 4/11/15.
 */
public interface Matcher {
    void add (String s, int tags);
    void add (String[] s, int tags);

    int match (String s, int i);
    int matchedChars ();
    int end ();
}
