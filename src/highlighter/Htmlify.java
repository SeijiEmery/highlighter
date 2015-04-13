package highlighter;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
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

    static final boolean SHOW_PROCESSED_DIRS = false;
    static final boolean SHOW_PROCESSED_FILES = true;

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
            threads = 30;
            System.out.printf("Using %d threads\n", threads);
            pool = new ThreadPool(threads);
//            pool = new ThreadPool(threads > 1 ? threads - 1 : threads);
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

        // Initializes the ThreadPool and spawns and runs n Worker threads.
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
        // Adds a task to be executed
        public synchronized void addTask (FileProcessTask task) {
            if (isStopped)
                throw new IllegalStateException("Threadpool is stopped");
            taskQueue.offer(task);
        }

        // Kills all child threads
        public synchronized void stop () {
            isStopped = true;
            for (Worker worker : workers)
                worker.stopThread();
        }

        // Returns true if there are no pending tasks and all child threads have finished execution
        public synchronized boolean done () {
//            if (!taskQueue.isEmpty())
//                return false;
//            for (Worker worker : workers)
//                if (worker.isRunningTask())
//                    return false;
//            return true;
            // Unnecessary to manually wait for threads to finish running, since they'll automatically terminate after
            // stop() is called (and not until then).
            return taskQueue.isEmpty();
        }
        // Manually pops a task off the taskQueue.
        // Enables the main thread to execute tasks alongside the workers if it's finished early and has nothing to do
        // besides sleep.
        public synchronized FileProcessTask getTask () throws InterruptedException {
            return taskQueue.take();
        }
        // Returns the list of thread stats
        public synchronized List<Stats> getStats () {
            return threadStats;
        }
    }

    // Worker thread that executes FileProcessingTasks
    static class Worker extends Thread {
        private boolean running = true;
        private final BlockingQueue<FileProcessTask> taskQueue;
        private final Parser parserInstance;
        private final Stats stats;

        // Creates a worker that operates on a given taskQueue.
        // parserInstance and stats should be unique instances of their respective classes that are owned
        // by this thread. Sharing instances between threads will cause data corruption and errors.
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
                    task.setInstanceVars(parserInstance, stats);
                    task.run();
                } catch (InterruptedException e) {
                    Thread.interrupted();
                }
            }
        }

        // Kills the thread
        public synchronized void stopThread () {
            running = false;
            this.interrupt();
        }

        // Returns whether the thread is currently running
        public synchronized boolean isStopped () {
            return !running;
        }
    }

    // Encapsulates a multithreaded htmlify operation on a single file
    class FileProcessTask implements Runnable {
        public final File inputFile;
        public final File outputFile;
        private Parser parser = null;
        private Stats  stats = null;

        FileProcessTask(File inputFile, File outputFile) {
            this.inputFile = inputFile;
            this.outputFile = outputFile;
        }

        // Sets thread-specific state; must be called before run
        public void setInstanceVars (Parser parser, Stats stats) {
            this.parser = parser;
            this.stats = stats;
        }

        // Executes the task on a given thread
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

    // Adds a FileProcessingTask to be executed by one of the worker threads
    void processFileMultithreaded(File inputFile, File outputFile) {
        assert(USE_MULTITHREADING == true);
        ++fileCount;
        pool.addTask(new FileProcessTask(inputFile, outputFile));
    }

    // Htmlifies a single file using the active thread
    void processFileSinglethreaded(File inputFile, File outputFile) {
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
            System.out.printf("Scanning '%s'\n", dir.getPath());

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
        for (File inputFile : inputFiles) {
            File outputDir = new File(dir.getPath().replace(rootPath, outputPath));
            if (!outputDir.exists())
                outputDir.mkdirs();
            File outputFile = new File(inputFile.getPath().replace(rootPath, outputPath).replace(".java", ".html"));
            if (USE_MULTITHREADING)
                processFileMultithreaded(inputFile, outputFile);
            else
                processFileSinglethreaded(inputFile, outputFile);
        }
        stats.endProcessingDir();
        for (File subdir : subdirs)
            processDir(subdir, rootPath, outputPath);
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
        Stats mainThreadStats = new ThreadStats();
        if (USE_MULTITHREADING) {
            // If tasks still aren't done, use this thread to augment the worker threads
            while (!htmlify.pool.done()) {
                try {
                    FileProcessTask task = htmlify.pool.getTask();
                    task.setInstanceVars(htmlify.parser, mainThreadStats);
                    task.run();
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
            System.out.println(mainThreadStats.getStats());
            System.out.println(stats.getStats());
        }
    }
}
