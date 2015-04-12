package highlighter;

import java.io.*;
import java.util.ArrayList;

/**
 * Created by Seiji on 4/8/15.
 */
public class Parser {
    Matcher textMatcher;

    static final int KEYWORD_FLAG               = 0x1 << 0;
    static final int SINGLE_LINE_COMMENT        = 0x1 << 1;
    static final int MULTILINE_COMMENT_BEGIN    = 0x1 << 2;
    static final int MULTILINE_COMMENT_END      = 0x1 << 3;
    static final int EOL                        = 0x1 << 4;
    static final int DOUBLE_QUOTE               = 0x1 << 5;
    static final int SINGLE_QUOTE               = 0x1 << 6;
    static final int ESCAPED_DOUBLE_QUOTE       = 0x1 << 7;
    static final int ESCAPED_SINGLE_QUOTE       = 0x1 << 8;
    static final int INT_LITERAL                = 0x1 << 9;
    static final int HEX_LITERAL                = 0x1 << 10;
    static final int DECIMAL_MARKER             = 0x1 << 11;
    static final int HEX_MARKER                 = 0x1 << 12;
    static final int TERMINAL                   = 0x1 << 13;

    final float foo = 10.4e-1f;
    final double bar = 10.2930;
    final int baz = 0x2af1e+21*6;
    String foob = ""+"";
    /* foo *//* bar */

    Parser (Matcher matcher) {
        long startTime = System.nanoTime();

        String[] keywords = {
                "abstract", "continue", "for", "new", "switch", "assert", "default", "goto", "package", "synchronized",
                "boolean", "do", "if", "private", "this", "break", "double", "implements", "protected", "throw", "byte",
                "else", "import", "public", "throws", "case", "enum", "instanceof", "return", "transient", "catch",
                "extends", "int", "short", "try", "char", "final", "interface", "static", "void", "class", "finally",
                "long", "strictfp", "volatile", "const", "float", "native", "super", "while"
        };
        textMatcher = matcher;
        textMatcher.add(keywords, KEYWORD_FLAG);
        textMatcher.add("//", SINGLE_LINE_COMMENT);
        textMatcher.add("/*", MULTILINE_COMMENT_BEGIN);
        textMatcher.add("*/", MULTILINE_COMMENT_END);

        textMatcher.add("\n", EOL);
        textMatcher.add("'",  SINGLE_QUOTE);
        textMatcher.add("\"", DOUBLE_QUOTE);
        textMatcher.add("\\'",  ESCAPED_SINGLE_QUOTE);
        textMatcher.add("\\\"", ESCAPED_DOUBLE_QUOTE);

        String[] integers = {
                "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"
        };
        String[] hex = {
                "A", "B", "C", "D", "E", "F", "a", "b", "c", "d", "e", "f"
        };
        textMatcher.add(integers, INT_LITERAL);
        textMatcher.add(hex, HEX_LITERAL);
        textMatcher.add(".", DECIMAL_MARKER);
        textMatcher.add("0x", HEX_MARKER);

        String[] terminals = {
                " ", "\t", "\n", ".", ",", ";", "(", ")", "{", "}", "[", "]",
                "+", "-", "*", "/", "%", "&", "|", "=", ":", "?"
        };
        textMatcher.add(terminals, TERMINAL);

        long insertionTime = System.nanoTime();

//        textMatcher.rebuild();
        long trieBuildTime = System.nanoTime();

        System.out.println("Parser structure: (DEBUG)");
        System.out.println(textMatcher);
        long printoutTime = System.nanoTime();

        double elapsedTime = (double)(printoutTime - startTime) * 1e-6;
        System.out.printf("Parser initialized in %f ms\n", elapsedTime);
        System.out.printf("\ttriebuilder:   %f ms\n", (double)(insertionTime - startTime) * 1e-6);
        System.out.printf("\ttrie rebuild:  %f ms\n", (double)(trieBuildTime - insertionTime) * 1e-6);
        System.out.printf("\ttrie printout: %f ms\n\n", (double)(printoutTime - trieBuildTime) * 1e-6);
    }

    enum TokenType {
        KEYWORD,
        LITERAL,
        COMMENT,
        TEXT
    }

    static class Token {
        String tok;
        TokenType type;

        Token (String s, int b, int e, TokenType type) {
            this.tok = s.substring(b, e);
            this.type = type;
        }
        Token (String s, int b, TokenType type) {
            this.tok = s.substring(b);
            this.type = type;
        }
        Token (String s, TokenType type) {
            this.type = type;
        }

        public String toString () {
            StringBuilder sb = new StringBuilder("Token (");
            switch (type) {
                case KEYWORD: sb.append("KEYWORD) '"); break;
                case LITERAL: sb.append("LITERAL) '"); break;
                case COMMENT: sb.append("COMMENT) '"); break;
                case TEXT: sb.append("TEXT) '"); break;
                default: sb.append("unknown) '"); break;
            }
            sb.append(tok);
            sb.append('\'');
            return sb.toString();
        }
    }

    // parse()-specific state
    ArrayList<Token> tokens;
    int start;
    int prev;

    // Utility functions
    void beginToken (String s, int i) {
        start = i;
        if (start != prev) {
            assert(start > prev);
            tokens.add(new Token(s, prev, start, TokenType.TEXT));
            prev = start;
        }
    }
    void endToken (String s, int i, TokenType type) {
        if (start != i) {
            assert(start < i);
            tokens.add(new Token(s, start, i, type));
            prev = i;
        }
    }
    boolean match (String s, int i, int type) {
        return (textMatcher.match(s, i) & type) != 0;
    }
    int next () {
        int incr = textMatcher.matchedChars();
        return incr != 0 ? incr : 1;
    }

    public ArrayList<Token> parse(String s) {
        tokens = new ArrayList<Token>();
        prev = 0;

        int e;   // tmp var

        long startTime = System.nanoTime();
        for (int i = 0, n = s.length(); i < n;) {
            switch (textMatcher.match(s, i)) {
                case SINGLE_LINE_COMMENT:
                    beginToken(s, i);
                    i = textMatcher.end();
                    while (i < n && !match(s, i, EOL))
                        i += next();
                    i = textMatcher.end();
                    endToken(s, i, TokenType.COMMENT);
                    break;
                case MULTILINE_COMMENT_BEGIN:
                    beginToken(s, i);
                    i = textMatcher.end();
                    while (i < n && !match(s, i, MULTILINE_COMMENT_END))
                        i += next();
                    if (i < n)
                        i = textMatcher.end();
                    endToken(s, i, TokenType.COMMENT);
                    break;
                case KEYWORD_FLAG:
                    e = textMatcher.end();
                    if (i > 0 && (!match(s, i-1, TERMINAL) || !match(s, e, TERMINAL))) {
                        ++i; continue;   // keyword not bounded by terminal characters (ie. it's inside another token)
                    }
                    beginToken(s, i);
                    endToken(s, e, TokenType.KEYWORD);
                    i = e;
                    break;
                case SINGLE_QUOTE:
                    beginToken(s, i);
                    i = textMatcher.end();
                    while (i < n) {
                        if (match(s, i, ESCAPED_SINGLE_QUOTE)) {
                            i += next();
                        } else if (match(s, i, SINGLE_QUOTE)) {
                            i += next();
                            break;
                        } else {
                            ++i;
                        }
                    }
                    endToken(s, i, TokenType.LITERAL);
                    break;
                case DOUBLE_QUOTE:
                    beginToken(s, i);
                    i = textMatcher.end();
                    while (i < n) {
                        if (match(s, i, ESCAPED_DOUBLE_QUOTE)) {
                            i += next();
                        } else if (match(s, i, DOUBLE_QUOTE)) {
                            i += next();
                            break;
                        } else {
                            ++i;
                        }
                    }
                    endToken(s, i, TokenType.LITERAL);
                    break;
                case INT_LITERAL:
                    e = textMatcher.end();
                    if (i > 0 && (!match(s, i-1, TERMINAL))) {
                        ++i; continue;
                    }
                    beginToken(s, i);
                    i = e;

                    while (i < n && (match(s, i, INT_LITERAL)))
                        i += next();
                    if (i < n && match(s, i, DECIMAL_MARKER)) {
                        // match float component...
                        i = textMatcher.end();
                        while (i < n && match(s, i, INT_LITERAL))
                            i += next();
                    }
                    if (i < n && s.charAt(i) == 'e') {
                        // match exp component
                        ++i;
                        if (i < n && s.charAt(i) == '-')
                            ++i;
                        while (i < n && match(s, i, INT_LITERAL))
                            i += next();
                    }
                    if (i < n && (s.charAt(i) == 'f' || s.charAt(i) == 'F'))  // match trailing 'f' (for floats)
                        ++i;
                    if (match(s, i, TERMINAL)) {
                        endToken(s, i, TokenType.LITERAL); // is valid int, float, or hex literal
                        i += next();
                    } else {
                        endToken(s, i, TokenType.TEXT);    // no match -- treat it like normal text instead
                        ++i;
                    }
                    break;
                case HEX_MARKER:
                    e = textMatcher.end();
                    if (i > 0 && (!match(s, i-1, TERMINAL))) {
                        ++i; continue;
                    }
                    beginToken(s, i);
                    i = e;
                    while (i < n && match(s, i, INT_LITERAL | HEX_LITERAL))
                        i += next();
                    if (match(s, i, TERMINAL)) {
                        endToken(s, i, TokenType.LITERAL);
                        i += next();
                    } else {
                        endToken(s, i, TokenType.TEXT);
                        ++i;
                    }
                    break;
                default:
                    ++i;
            }
        }
        beginToken(s, s.length());  // adds last token

        long endTime = System.nanoTime();
        System.out.printf("\tparsing: %f ms\n", (double)(endTime - startTime) * 1e-6);
//        System.out.println("Input:");
//        System.out.println(s);
//        System.out.println("Tokens:");
//        for (Token tok : tokens) {
//            System.out.println(tok);
//        }
//        System.out.println("html:");
//        System.out.println(produceHtml(tokens));
        return tokens;
    }

    public String makeHtml(String sourceCode, String cssLink) {
        System.out.printf("Processing source code\n");
        long startTime = System.nanoTime();
        ArrayList<Token> tokens = parse(sourceCode);

        long htmlStart = System.nanoTime();
        StringBuilder sb = new StringBuilder();
        if (cssLink != null)
            sb.append(String.format("<head><link href=\"%s\" type=\"text/css\" rel=\"stylesheet\" /></head>", cssLink));
        else // use default embedded css
            sb.append(String.format("<head><style>%s</style></head>", defaultCss));
        sb.append("<body><pre class=\"prettyprint\"><code>");
        spanify(tokens, sb);
        sb.append("</code></pre></body>");
        long htmlEnd = System.nanoTime();

        System.out.printf("\thtml generation: %f ms\n", (double)(htmlEnd - htmlStart) * 1e-6);
        System.out.printf("\ttotal time: %f ms\n\n", (double)(htmlEnd - startTime) * 1e-6);

        return sb.toString();
    }
    public String makeHtml (String sourceCode) {
        return makeHtml(sourceCode, null);
    }

    private void spanify (ArrayList<Token> tokens, StringBuilder sb) {
        for (Token token : tokens) {
            switch (token.type) {
                case KEYWORD:   sb.append("<span class=\"kwd\">"); break;
                case LITERAL:   sb.append("<span class=\"lit\">"); break;
                case COMMENT:   sb.append("<span class=\"com\">"); break;
                case TEXT:      sb.append("<span class=\"pln\">"); break;
            }
            // sb.append(token.tok);

            // check each character to produce properly escaped html characters
            for (int i = 0, n = token.tok.length(); i < n; ++i) {
                char c = token.tok.charAt(i);
                switch (c) {
                    case '<': sb.append("&lt;"); break;
                    case '>': sb.append("&gt;"); break;
                    case '&': sb.append("&amp;"); break;
                    default: sb.append(c);
                }
            }
//            if (token.type != TokenType.TEXT)
                sb.append("</span>");
        }
    }

    static final String defaultCss = "" +
            "pre.prettyprint { display: block }\n" +
            "pre .nocode { background-color: none; color: #000 }\n" +
            "pre .kwd { color: navy; font-weight: bold }\n" +
            "pre .com { color: green; font-weight: bold } /* comment  */\n" +
            "pre .lit { color: blue; font-weight: bold; } /* literal  */\n" +
            "@media print {\n" +
            "  pre.prettyprint { background-color: none }\n" +
            "  pre .kwd, code .kwd { color: navy; font-weight: bold }\n" +
            "  pre .com, code .com { color: green; font-style: bold }\n" +
            "  pre .lit, code .lit { color: blue; font-weight: bold; }\n" +
            "}";

    public static void main (String[] args) {
        String inputFile = null;
        String outputFile = null;
        String cssFile = null;

        if (args.length == 2) {
            inputFile = args[0];
            outputFile = args[1];
        } else if (args.length == 3) {
            inputFile = args[0];
            outputFile = args[1];
            cssFile   = args[2];
        } else {
            System.err.println("Usage: Parser <input file> <output file> [<css file>]");
            System.exit(-1);
        }

        if (!inputFile.endsWith(".java")) {
            System.err.println("source must be a java file");
            System.exit(-1);
        }
        if (cssFile != null && !cssFile.endsWith(".css")) {
            System.err.println("style must be a css file");
            System.exit(-1);
        }
        if (!outputFile.endsWith(".html")) {
            System.err.println("output file must be a html file");
            System.exit(-1);
        }
        if (!new File(inputFile).exists()) {
            System.err.printf("source file '%s' does not exist\n", inputFile);
            System.exit(-1);
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
            int c;
            while ((c = reader.read()) != -1)
                sb.append((char)c);
        } catch (FileNotFoundException e) {
            System.err.printf("Could not find file '%s'\n", args[0]);
            e.printStackTrace();
        } catch (IOException e) {
            System.err.printf("Error reading file '%s'\n", args[0]);
            e.printStackTrace();
        }
        String sourceCode = sb.toString();
        String html = new Parser(new StringMatcher()).makeHtml(sourceCode, cssFile);
        String html2 = new Parser(new NaiveMatcher()).makeHtml(sourceCode, cssFile);
        assert(html2.equals(html));
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            writer.write(html);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.printf("'%s' written successfully\n", outputFile);
    }





}
