package highlighter;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Seiji on 4/11/15.
 */
public class NaiveMatcher implements Matcher {
    Map<String, Integer> sequences = new HashMap<>();
    int maxLen = 0;

    public NaiveMatcher () {}
    public NaiveMatcher (String s, int tags) {
        add(s, tags);
    }
    public NaiveMatcher (String[] s, int tags) {
        add(s, tags);
    }

    @Override
    public void add(String s, int tags) {
        if (s.length() > maxLen)
            maxLen = s.length();
        if (sequences.containsKey(s)) {
            sequences.put(s, sequences.get(s) | tags);
        } else {
            sequences.put(s, tags);
        }
    }

    @Override
    public void add(String[] s, int tags) {
        for (String str : s) {
            add(str, tags);
        }
    }

    private int matched = 0;
    private int next = 0;

    @Override
    public int match(String s, int i) {
        int lastMatch = -1;
        int lastMatchTags = 0;

        for (int j = i+1, n = Math.min(s.length(), i + maxLen); j < n; ++j) {
            String substr = s.substring(i, j);
            if (sequences.containsKey(substr)) {
                lastMatch = j;
                lastMatchTags = sequences.get(substr);
            }
        }

        if (lastMatch > 0) {
            matched = lastMatch - i;
            next    = lastMatch;
            return lastMatchTags;
        } else {
            matched = 0;
            next = i;
            return lastMatchTags;
        }
    }

    @Override
    public int matchedChars() {
        return matched;
    }

    @Override
    public int end() {
        return next;
    }
}
