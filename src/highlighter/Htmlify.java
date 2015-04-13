package highlighter;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by Seiji on 4/11/15.
 *
 * Program that recursively scans a source directory for .java files and generates mirrored html-ified files in the
 * target directory
 */
public class Htmlify {
//    private final Parser parser = new Parser(new StringMatcher());
    private final Stats stats;
    private final Parser parser;

    static final boolean USE_MULTITHREADING = true;
    static final boolean USE_NAIVE_MATCHER  = false;
    static final boolean USE_FAST_STATS     = true;

    public String cssLink = null;
    ThreadPool pool;

    public Htmlify (Stats stats, String cssLink) {
        this.stats = stats;

        this.parser = USE_NAIVE_MATCHER ?
                new Parser(new NaiveMatcher(stats), stats) :
                new Parser(new StringMatcher(stats), stats);
        this.cssLink = cssLink;

        int threads = Runtime.getRuntime().availableProcessors();
        assert(threads > 0);
        if (USE_MULTITHREADING)
            pool = new ThreadPool(threads > 1 ? threads - 1 : threads);
        else
            pool = null;
    }

    @Override
    public void finalize () {
        if (USE_MULTITHREADING)
            pool.stop();
    }

    private long parseTime;
    private int fileCount = 0;

    public double getParseTime () {
        return (double)(parseTime) * 1e-6;
    }
    public int getFileCount () {
        return fileCount;
    }
    public void reset () {
        parseTime = 0;
        fileCount = 0;
    }

    class ThreadPool {
        private final List<Worker> workers = new ArrayList<>();
        private final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();
        private boolean isStopped = false;

        ThreadPool (int numThreads) {
            for (int i = 0; i < numThreads; ++i)
                workers.add(new Worker(taskQueue));
            for (Worker worker : workers) {
                worker.start();
            }
        }
        public synchronized void addTask (Runnable task) {
            if (isStopped)
                throw new IllegalStateException("Threadpool is stopped");
            taskQueue.offer(task);
        }
        public synchronized void stop () {
            isStopped = true;
            for (Worker worker : workers)
                worker.stopThread();
        }
    }

    static class Worker extends Thread {
        private boolean running = true;
        private final BlockingQueue<Runnable> taskQueue;

        Worker (BlockingQueue<Runnable> taskQueue) {
            this.taskQueue = taskQueue;
        }

        @Override
        public void run () {
            running = true;
            while (running) {
                try {
                    Runnable runnable = taskQueue.take();
                    runnable.run();
                } catch (InterruptedException e) {
                    Thread.interrupted();
                }
            }
        }
        public synchronized void stopThread () {
            running = false;
            this.interrupt();
        }
        public synchronized boolean isStopped () {
            return !running;
        }
    }

    class FileProcessTask implements Runnable {
        public final File inputFile;
        public final File outputFile;

        FileProcessTask(File inputFile, File outputFile) {
            this.inputFile = inputFile;
            this.outputFile = outputFile;
        }

        public void run() {
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
            String html = parser.makeHtml(source, cssLink);

            try (BufferedWriter br = new BufferedWriter(new FileWriter(outputFile))) {
                br.write(html);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    void processFile (File inputFile, File outputFile) {
        ++fileCount;
        pool.addTask(new FileProcessTask(inputFile, outputFile));
    }

    // singlethreaded only
    void generateHtml (File inputFile, File outputFile) {
        stats.beginProcessingFile();
        ++fileCount;
        stats.beginFileRead();
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
        stats.endFileRead();
        String html = parser.makeHtml(source, cssLink);

        stats.beginFileWrite();
        try (BufferedWriter br = new BufferedWriter(new FileWriter(outputFile))) {
            br.write(html);
        } catch (IOException e) {
            e.printStackTrace();
        }
        stats.endFileWrite();
        stats.endProcessingFile();
    }

    void processDir (File dir, String rootPath, String outputPath) {
        assert(dir.isDirectory() && dir.exists());
        stats.beginProcessingDir();

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
        stats.endProcessingDir();
        for (File subdir : subdirs)
            processDir(subdir, rootPath, outputPath);

        for (File inputFile : inputFiles) {
            File outputDir = new File(dir.getPath().replace(rootPath, outputPath));
            if (!outputDir.exists())
                outputDir.mkdirs();
            File outputFile = new File(inputFile.getPath().replace(rootPath, outputPath).replace(".java", ".html"));
            if (USE_MULTITHREADING)
                processFile(inputFile, outputFile);
            else
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

        final Stats stats = USE_FAST_STATS ?
                new FastStats() :
                new TimedStats();

        final Htmlify htmlify = new Htmlify(stats, css);

//        long startTime = System.nanoTime();

        stats.startHtmlify();
        htmlify.processDir(new File(inputDir), inputDir, outputDir);
        stats.endHtmlify();

//        double elapsedTime = (double)(System.nanoTime() - startTime) * 1e-6;
        System.out.printf("finished processing '%s'\n", inputDir);
        System.out.printf("generated %d files in '%s'\n", htmlify.getFileCount(), outputDir);
//        System.out.printf("\tparse time: %f ms\n", htmlify.getParseTime());
//        System.out.printf("\toverhead:   %f ms\n", elapsedTime - htmlify.getParseTime());
//        System.out.printf("\ttotal run time: %f ms\n", elapsedTime);

        System.out.println(stats.getAdjustedStats());
    }
}
