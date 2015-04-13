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
    static final boolean DISPLAY_SIMPLE_STATS = true;

    static final boolean SHOW_PROCESSED_DIRS = true;
    static final boolean SHOW_PROCESSED_FILES = false;

    public String cssLink = null;
    ThreadPool pool;

    public Htmlify (Stats stats, String cssLink) {
        this.stats = stats;

        this.parser = USE_NAIVE_MATCHER ?
                new Parser(new NaiveMatcher(stats), stats) :
                new Parser(new StringMatcher(stats), stats);
        this.cssLink = cssLink;
        if (USE_MULTITHREADING) {
            int threads = Runtime.getRuntime().availableProcessors();
            assert(threads > 0);
            System.out.printf("Using %d threads\n", threads);
            pool = new ThreadPool(threads);
//            pool = new ThreadPool(threads > 2 ? threads - 1 : threads);
        } else {
            pool = null;
        }
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
        private final List<Stats>  threadStats = new ArrayList<>();
        private final BlockingQueue<FileProcessTask> taskQueue = new LinkedBlockingQueue<>();
        private boolean isStopped = false;

        ThreadPool (int numThreads) {
            for (int i = 0; i < numThreads; ++i) {
//                Stats stats = USE_FAST_STATS ? new FastStats() : new TimedStats();
                Stats stats = new ThreadStats();
                threadStats.add(stats);
                workers.add(new Worker(taskQueue, new Parser(parser, stats), stats));
            }
            for (Worker worker : workers) {
                worker.start();
            }
        }
        public synchronized void addTask (FileProcessTask task) {
            if (isStopped)
                throw new IllegalStateException("Threadpool is stopped");
            taskQueue.offer(task);
        }
        public synchronized void stop () {
            isStopped = true;
            for (Worker worker : workers)
                worker.stopThread();
        }
        public synchronized boolean done () {
            return taskQueue.isEmpty();
        }


        public synchronized List<Stats> getStats () {
            return threadStats;
        }
    }

    static class Worker extends Thread {
        private boolean running = true;
        private final BlockingQueue<FileProcessTask> taskQueue;
        private final Parser parserInstance;
        private final Stats stats;

        Worker (BlockingQueue<FileProcessTask> taskQueue, Parser parserInstance, Stats stats) {
            this.taskQueue = taskQueue;
            this.parserInstance = parserInstance;
            this.stats = stats;
        }

        @Override
        public void run () {
            running = true;
            while (running) {
                try {
                    FileProcessTask task = taskQueue.take();
//                    task.setParser(parserInstance);
                    task.setInstanceVars(parserInstance, stats);
                    task.run();
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
        private Parser parser = null;
        private Stats stats = null;

        FileProcessTask(File inputFile, File outputFile) {
            this.inputFile = inputFile;
            this.outputFile = outputFile;
        }
        public void setInstanceVars (Parser parser, Stats stats) {
            this.parser = parser;
            this.stats = stats;
        }
        public void run() {
            stats.beginProcessingFile();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
                int c;
                while ((c = reader.read()) != -1)
                    sb.append((char)c);
            } catch (FileNotFoundException e) {
                System.err.printf("Error reading '%s'\n", inputFile);
                e.printStackTrace();
            } catch (IOException e) {
                System.err.printf("Error reading '%s'\n", inputFile);
                e.printStackTrace();
            }
            String source = sb.toString();
            String html = null;

            try {
                html = parser.makeHtml(source, cssLink);
            } catch (Exception ex) {
                System.err.printf("Error parsing '%s' in thread '%s'\n", inputFile, Thread.currentThread().getName());
                ex.printStackTrace(System.err);
                return;
            }
            try (BufferedWriter br = new BufferedWriter(new FileWriter(outputFile))) {
                br.write(html);
            } catch (IOException e) {
                System.err.printf("Error writing to '%s'\n", outputFile);
                e.printStackTrace();
            }
            if (SHOW_PROCESSED_FILES)
                System.out.printf("Processed '%s'\n", inputFile);
            stats.endProcessingFile();
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
        if (SHOW_PROCESSED_FILES)
            System.out.printf("Processed '%s'\n", inputFile);
    }

    void processDir (File dir, String rootPath, String outputPath) {
        assert(dir.isDirectory() && dir.exists());
        stats.beginProcessingDir();

        if (SHOW_PROCESSED_DIRS)
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
        if (USE_MULTITHREADING) {
            while (!htmlify.pool.done()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ex) {
                    Thread.interrupted();
                }
            }
            htmlify.pool.stop();
        }
        stats.endHtmlify();

//        double elapsedTime = (double)(System.nanoTime() - startTime) * 1e-6;
        System.out.printf("finished processing '%s'\n", inputDir);
        System.out.printf("generated %d files in '%s'\n", htmlify.getFileCount(), outputDir);
//        System.out.printf("\tparse time: %f ms\n", htmlify.getParseTime());
//        System.out.printf("\toverhead:   %f ms\n", elapsedTime - htmlify.getParseTime());
//        System.out.printf("\ttotal run time: %f ms\n", elapsedTime);

        if (DISPLAY_SIMPLE_STATS)
            System.out.println(stats.getStats());
        else
            System.out.println(stats.getAdjustedStats());

//        if (USE_MULTITHREADING && !USE_FAST_STATS) {
//            System.out.println("Warning: profiler information is unpredictable when used with multithreading");
//        }

        if (USE_MULTITHREADING) {
            int i = 0;
            for (Stats threadStats : htmlify.pool.getStats()) {
                System.out.printf("Thread %d stats:\n", i++);
                System.out.println(threadStats.getStats());
            }
            System.out.println("Main thread stats: ");
            System.out.println(stats.getStats());
        }
    }
}
