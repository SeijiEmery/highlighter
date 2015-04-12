package highlighter;

import java.io.*;

/**
 * Created by Seiji on 4/11/15.
 *
 * Program that recursively scans a source directory for .java files and generates mirrored html-ified files in the
 * target directory
 */
public class Htmlify {
    private final Parser parser = new Parser(new NaiveMatcher());
    public String cssLink = null;

    public Htmlify (String cssLink) {
        this.cssLink = cssLink;
    }

    private long parseTime;

    public double getParseTime () {
        return (double)(parseTime) * 1e-6;
    }
    public void reset () {
        parseTime = 0;
    }


    void generateHtml (File inputFile, File outputFile) {
        System.out.printf("Processing '%s'\n", inputFile.getPath());
//        String source = null;
//        try {
//            source = String.join("\n", Files.readAllLines(inputFile.toPath()));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
            int c;
            while ((c = reader.read()) != -1)
                sb.append((char)c);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        String source = sb.toString();
        long t0 = System.nanoTime();
        String html = parser.makeHtml(source, cssLink);
        parseTime += (System.nanoTime() - t0);

        try (BufferedWriter br = new BufferedWriter(new FileWriter(outputFile))) {
            br.write(html);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.printf("Generated '%s'\n\n", outputFile.getPath());
    }

    void processDir (File dir, String rootPath, String outputPath) {
        assert(dir.isDirectory() && dir.exists());

        System.out.printf("Processing '%s'\n", dir.getPath());

        File[] subdirs = dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isDirectory() && pathname.exists();
            }
        });
        File[] inputFiles = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".java");
            }
        });
        for (File subdir : subdirs)
            processDir(subdir, rootPath, outputPath);

        for (File inputFile : inputFiles) {
            File outputDir = new File(dir.getPath().replace(rootPath, outputPath));
            if (!outputDir.exists())
                outputDir.mkdirs();
            File outputFile = new File(inputFile.getPath().replace(rootPath, outputPath).replace(".java", ".html"));
            generateHtml(inputFile, outputFile);
        }
    }

    public static void main (String[] args) {
        String inputDir = null;
        String outputDir = null;
        String css = null;
        if (args.length == 2) {
            inputDir = args[0];
            outputDir = args[1];
        } else if (args.length == 3) {
            inputDir = args[0];
            outputDir = args[1];
            css = args[2];
        } else {
            System.err.println("usage: Htmlify <input dir> <output dir> [<css file>]");
            System.exit(-1);
        }
        if (css != null && !css.endsWith(".css")) {
            System.err.printf("'%s' must be a css file", css);
            System.exit(-1);
        }

        Htmlify htmlify = new Htmlify(css);

        long startTime = System.nanoTime();
        htmlify.processDir(new File(inputDir), inputDir, outputDir);
        double elapsedTime = (double)(System.nanoTime() - startTime) * 1e-6;
        System.out.printf("processed all files in '%s'\n", inputDir);
        System.out.printf("\ttotal run time: %f ms\n", elapsedTime);
        System.out.printf("\tparse time: %f ms\n", htmlify.getParseTime());
    }
}
