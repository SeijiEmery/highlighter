package highlighter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Seiji on 4/8/15.
 *
 * Implements a simple text matcher that works by associatively mapping specific, non-recursive character sequences to
 * a set of integer tags (represented internally by a bitfield). This is not quite as powerful as regexes, but is
 * perfectly suitable for building a simple text parser, and is quite a bit easier to use than trying to wrangle java's
 * regex utilites into a parser framework (which is not their intended purpose, and doesn't work very well).
 *
 * Internally, this is implemented as a trie (for efficiency's sake), and uses nested hashmaps to construct the parse
 * rules via the TrieBuilder class (since the Trie data structure is immutable).
 *
 * Usage is simple: you create a StringMatcher instance, and define several matcher rules for it to operate on:
 *    StringMatcher matcher = new StringMatcher();
 *    matcher.add("\n", EOL_TAG);
 *    matcher.add("foo", FOO_TAG);
 *    String[] numbers = { "1", "2", "3", "4", "5", "6", "7", "8", "9", "0" };
 *    matcher.add(numbers, NUMERIC_TAG);
 *
 * Once you're finished, call rebuild() to create the trie instance (technically optional since this will get called the first
 * time you use call match(), if it hasn't been initialized already)
 *    matcher.rebuild();
 *
 * Matching is done by passing in a string and a start index. The matcher will match as far as possible, and return the tags
 * that it found at the end of the sequence.
 *    if (matcher.match(s, i) & EXPECTED_TAG != 0)
 *       // handle match here...
 *
 * The number of matches found and index at the end of the match can be retrieved via matcher.matchedChars() and matcher.end().
 * These are both specific to the last call to match(), and should not be used if the last match result was unsatisfactory.
 * However, match.end() always returns the index of the next character to parse iff the previous result was ok (not discarded),
 * which has some nice properties:
 *
 * match all characters in a given sequence (eg. part of an integer literal)
 *    int start = i;
 *    while (i > s.length() && (matcher.match(s, i) & INTEGER_TAG) != 0)
 *       i = match.end();
 *    if (start != i)   // at least one match occured, so we can do something with it:
 *       emitIntegerPart(s, start, i);
 *
 * match all characters up to a given sequence (eg. matching a string literal or comment sequence)
 *    int start = i;
 *    while (i > s.length() && (matcher.match(s, i) & END_TAG) == 0)
 *       i = match.end();   // or ++i;  -- that works too, and it might be better if multiple sequence sets overlap
 *                          // (i = match.end() is faster, but ++i checks each character individually and ensures
 *                          //  you aren't accidentally skipping over anything)
 *    int end = i;
 *    //...
 *
 */
public class StringMatcher implements Matcher {

    private final Stats stats;

    // Utility class used internally to store match rules and rebuild the trie
    // This is a mutable (but slightly inefficient) implementation of the StringMatcher, and stores the same data
    // as the immutable, but much more efficient trie data structure.
    static class TrieBuilder {
        public final Map<Character, TrieBuilder> values = new HashMap<>();
        public char min = Character.MAX_VALUE;
        public char max = Character.MIN_VALUE;
        public int tags = 0;

        void add (String s, int i, int tag) {
            if (i >= s.length()) {
                tags |= tag;
                return;
            }
            char c = s.charAt(i);
            if (c > max)   max = c;
            if (c < min)   min = c;
            if (!values.containsKey(c))
                values.put(c, new TrieBuilder());
            values.get(c).add(s, i+1, tag);
        }
        void add (String s, int tag) {
            add(s, 0, tag);
        }

        public Trie build () {
            return new Trie(this);
        }
    }

    // Internal data structure used to map strings to integer tags in a highly compact form.
    // Matching looks up the longest string sequence and returns its tags (which can be compared to an expected set of
    // tags to determine whether the match was successful or not).
    static class Trie {
        final char first;   // offset for the first char in this trie. char range == [first, first + next.length)
        final Trie[] next;  // next elements in the trie
        final int count;    // number of elements in the trie (cached). NOT equal to next.length
        final int tags;     // bitfield containing the tags that this character sequence is mapped to

        private Trie () {
            this.next = null;
            this.first = 0;
            this.tags = 0;
            this.count = 0;
        }

        // Trie must always (normally) be constructed from a TrieBuilder instance.
        // This invokes recursive calls that construct the a trie from the (presumably) root TrieBuilder node.
        public Trie (TrieBuilder b) {
            assert((int)b.max > (int)b.min);
            assert((int)b.max - (int)b.min >= 0);
//            System.out.printf("%d %d %d\n", (int)b.max, (int)b.min, (int)(b.max - b.min));

            if (b.values.size() == 0)
                this.next = null;
            else
                this.next = new Trie[b.max - b.min + 1];
            this.first = b.min;
            this.tags = b.tags;
            this.count = b.values.size();

            for (Map.Entry<Character, TrieBuilder> e : b.values.entrySet()) {
                next[e.getKey() - first] = new Trie(e.getValue());
            }
        }
        private Trie getNext (char c) {
            int i = c - first;
            if (next == null || i < 0 || i >= next.length)
                return null;
            return next[i];
        }
        public int match (String s, int i, StringMatcher matcher) {
            Trie trie = this;
            for (final int n = s.length(); i < n; ++i) {
                Trie next_ = trie.getNext(s.charAt(i));
                if (next_ != null) {
                    trie = next_;
                } else {
                    matcher.tags = trie.tags;
                    return i;
                }
            }
            // at end of string, so return last matched
            matcher.tags = trie.tags;
            return i;
        }

        // toString() helper function
        // (lists the numeric flags stored in this node's tag bitfield)
        public void printFlags (StringBuilder sb) {
            for (int i = 0; i < 31; ++i) {
                if ((tags & (1 << i)) != 0)
                    sb.append(String.format("%d", i)).append(' ');
            }
        }
        // toString() helper function
        private static void printIndent (StringBuilder sb, int indent) {
            for (int i = 0; i < indent; ++i)
                sb.append("  ");
        }
        protected void toString (char c, StringBuilder sb, int indent) {
            printIndent(sb, indent);
            if (c < 32)
                sb.append(String.format("(%d)  ", (int)c));
            else
                sb.append(String.format("'%c'  ", c));
            printFlags(sb);
            sb.append('\n');
            if (next != null) {
                for (int i = 0; i < next.length; ++i) {
                    if (next[i] != null)
                        next[i].toString((char)(i + first), sb, indent+1);
                }
            }
        }
        public String toString () {
            StringBuilder sb = new StringBuilder("\nTrie: ");
            printFlags(sb);
            sb.append('\n');

            if (next != null) {
                for (int i = 0; i < next.length; ++i) {
                    if (next[i] != null)
                        next[i].toString((char)(i + first), sb, 1);
                }
            }
            return sb.toString();
        }
    }
    TrieBuilder builder;    // mutable data structure used to rebuild the trie
    Trie trie = null;       // immutable, highly efficient data structure used to perform match lookups

    // Temporary state from the last match() call
    int matched;
    int last;
    int tags;

    // Construct a new StringMatcher
    public StringMatcher (Stats stats) {
        this.stats = stats;
        this.builder = new TrieBuilder();
    }

    // Construct a new StringMatcher and insert the following strings with the given tag
    public StringMatcher(Stats stats, String[] strings, int tag) {
        this.stats = stats;
        builder = new TrieBuilder();
        for (String s : strings)
            builder.add(s, tag);
    }
    // Construct a new StringMatcher and insert the following strings with the given tag
    public StringMatcher (Stats stats, ArrayList<String> strings, int tag) {
        this.stats = stats;
        builder = new TrieBuilder();
        for (String s : strings)
            builder.add(s, tag);
    }

    // Add a rule to match the following string to the given tag
    public void add (String s, int tag) {
        stats.beginTrieInit();
        builder.add(s, tag);
        stats.endTrieInit();
    }

    // Add a rule to match the following strings to the given tag
    public void add (String[] strings, int tag) {
        stats.beginTrieInit();
        for (String s : strings)
            add(s, tag);
        stats.endTrieInit();
    }

    // Rebuilds the internal trie structure from the current set of rules.
    // Should call this once, *after* all calls to add(...) have been made, but before calling match.
    // If match() has been called but additional rules have been added since the last call, call this to update
    // the internal data structure (without this, calls to add() have no effect after the first call).
    public void rebuild() {
        stats.beginTrieInit();
        trie = builder.build();
        stats.endTrieInit();
    }

    // Checks the following string starting at index i against the string matcher, matching as far as possible and
    // returning the tags from this full or partial match.
    public int match (String s, int i)  {
        if (trie == null) {
            rebuild();
        }
        if (i < 0 || i >= s.length()) {
            matched = 0;
            last = i;
            return 0;
        } else {
            stats.beginTrieMatch();
            last = trie.match(s, i, this);
            stats.endTrieMatch();
            matched = last - i;
            return tags;
        }
    }

    // Returns the number of chars matched from the last call to match()
    public int matchedChars () {
        return matched;
    }

    // Returns the index of the character after the last one processed by match().
    // Can advance fully through a string via repeated calls to match(s, i) and i = end(),
    // but this result should *only* be used if the result from the last match call was satisfactory.
    // eg.
    //      for (int i = 0; i < s.length(); ) {
    //          if ((matcher.match(s, i) & EXPECTED_RESULT) != 0)
    //              i = matcher.end();
    //          else
    //              break;
    //      }
    //      // matches everything that has the EXPECTED_RESULT tag until the first non EXPECTED_RESULT
    //      // sequence and/or the end of the string
    //
    public int end () {
        return last;
    }

    // Returns the nested printout of the internal trie data structure. Useful for debugging purposes only.
    public String toString () {
        if (trie == null)
            rebuild();
        return trie.toString();
    }
}
