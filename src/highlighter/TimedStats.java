package highlighter;

import java.sql.Time;

/**
 * Created by Seiji on 4/12/15.
 */
public class TimedStats implements Stats {
    private long parserInitTime = 0, parserInitStart = 0;
    private long trieInitTime = 0, trieInitStart   = 0;

    private long trieMatchTime = 0, trieMatchStart  = 0;
    private long htmlGenTime    = 0, htmlGenStart    = 0;
    private long parseTime      = 0, parseStart = 0;
    private long fileReadTime   = 0, fileReadStart = 0;
    private long fileWriteTime  = 0, fileWriteStart = 0;
    private long fileProcessTime = 0, fileProcessStart = 0;
    private long dirProcessTime = 0, dirProcessStart  = 0;
    private long totalTime      = 0, totalStart = 0;

    private int parserInitCalls = 0;
    private int trieInitCalls   = 0;
    private int trieMatchCalls  = 0;
    private int htmlGenCalls = 0;
    private int parseCalls  = 0;
    private int fileReadCalls   = 0;
    private int fileWriteCalls  = 0;
    private int fileProcessCalls = 0;
    private int dirProcessCalls = 0;
    private int htmlifyCalls  = 0;

    private int nanoCalls = 0;

    private long curTime () {
        ++nanoCalls;
        return System.nanoTime();
    }
    private long deltaTime (long start) {
        ++nanoCalls;
        return System.nanoTime() - start;
    }

    public void beginParserInit () {
        parserInitStart = curTime();
    }
    public void endParserInit () {
        parserInitTime += deltaTime(parserInitStart);
        ++parserInitCalls;
    }
    public void beginTrieInit () {
        trieInitStart = curTime();
    }
    public void endTrieInit () {
        trieInitTime += deltaTime(trieInitStart);
        ++trieInitCalls;
    }
    public void beginTrieMatch () {
        trieMatchStart = curTime();
    }
    public void endTrieMatch () {
        trieMatchTime += deltaTime(trieMatchStart);
        ++trieMatchCalls;
    }
    public void beginParse () {
        parseStart = curTime();
    }
    public void endParse () {
        parseTime += deltaTime(parseStart);
        ++parseCalls;
    }
    public void beginHtmlGen () {
        htmlGenStart = curTime();
    }
    public void endHtmlGen () {
        htmlGenTime += deltaTime(htmlGenStart);
        ++htmlGenCalls;
    }
    public void beginFileRead () {
        fileReadStart = curTime();
    }
    public void endFileRead () {
        fileReadTime += deltaTime(fileReadStart);
        ++fileReadCalls;
    }
    public void beginFileWrite () {
        fileWriteStart = curTime();
    }
    public void endFileWrite () {
        fileWriteTime += deltaTime(fileWriteStart);
        ++fileWriteCalls;
    }
    public void beginProcessingFile () {
        fileProcessStart = curTime();
    }
    public void endProcessingFile () {
        fileProcessTime += deltaTime(fileProcessStart);
        ++fileProcessCalls;
    }
    public void beginProcessingDir () {
        dirProcessStart = curTime();
    }
    public void endProcessingDir () {
        dirProcessTime += deltaTime(dirProcessStart);
        ++dirProcessCalls;
    }
    public void startHtmlify () {
        totalStart = System.nanoTime();
    }
    public void endHtmlify () {
        totalTime += System.nanoTime() - totalStart;
        ++htmlifyCalls;
    }

    public double toMs (long ns) {
        return (double)(ns) * 1e-6;
    }

    public String getStats () {
        StringBuilder sb = new StringBuilder();
        sb.append('\n');
        sb.append("=============================================================\n");
        sb.append("=                          Stats                            =\n");
        sb.append("=============================================================\n");
        sb.append("initialization:");
        sb.append("\n    parser init: ").append(toMs(parserInitTime));
        sb.append("\n    trie init:   ").append(toMs(trieInitTime));
        sb.append("\nrun time:");
        sb.append("\n    dir process time:  ").append(toMs(dirProcessTime));
        sb.append("\n    file process time: ").append(toMs(fileProcessTime));
        sb.append("\n        file read time:  ").append(toMs(fileReadTime));
        sb.append("\n        file write time: ").append(toMs(fileWriteTime));
        sb.append("\n        html gen:        ").append(toMs(htmlGenTime));
        sb.append("\n        parser:          ").append(toMs(parseTime));
        sb.append("\n            trie match:  ").append(toMs(trieMatchTime));
        sb.append("\n            parsing:     ").append(toMs(parseTime - trieMatchTime));
        sb.append("\n        overhead: ").append(toMs(fileProcessTime - htmlGenTime - parseTime - fileReadTime - fileWriteTime));
        sb.append("\n    overhead: ").append(toMs(totalTime - dirProcessTime - fileProcessTime));
        sb.append("\ntotal: ").append(toMs(totalTime));
        sb.append("\n\nSystem.nanoTime() calls: ").append(nanoCalls);
        sb.append(String.format("\nestimated profiling overhead:\n\t%d * 65ms / 1e6 calls = %f ms\n", nanoCalls, nanoCalls * 65.0f * 1e-6));
        return sb.toString();
    }

    public TimedStats getProfilerOverhead () {
        TimedStats stats = new TimedStats();
        for (int i = 0; i < parserInitCalls; ++i) {
            stats.beginParserInit();
            stats.endParserInit();
        }
        for (int i = 0; i < trieInitCalls; ++i) {
            stats.beginTrieInit();
            stats.endTrieInit();
        }
        for (int i = 0; i < dirProcessCalls; ++i) {
            stats.beginProcessingDir();
            stats.endProcessingDir();
        }
        for (int i = 0; i < fileProcessCalls; ++i) {
            stats.beginProcessingFile();
            stats.endProcessingFile();
        }
        for (int i = 0; i < fileReadCalls; ++i) {
            stats.beginFileRead();
            stats.endFileRead();
        }
        for (int i = 0; i < fileWriteCalls; ++i) {
            stats.beginFileWrite();
            stats.endFileWrite();
        }
        for (int i = 0; i < htmlGenCalls; ++i) {
            stats.beginHtmlGen();
            stats.endHtmlGen();
        }
        for (int i = 0; i < parseCalls; ++i) {
            stats.beginParse();
            stats.endParse();
        }
        for (int i = 0; i < trieMatchCalls; ++i) {
            stats.beginTrieMatch();
            stats.endTrieMatch();
        }
        for (int i = 0; i < htmlifyCalls; ++i) {
            stats.startHtmlify();
            stats.endHtmlify();
        }
        return stats;
    }
    public String getAdjustedStats () {
        TimedStats overhead = getProfilerOverhead();

        overhead.parseTime += overhead.trieMatchTime;
        overhead.fileProcessTime += overhead.parseTime + overhead.htmlGenTime +
                overhead.fileReadTime + overhead.fileWriteTime;
        overhead.totalTime += overhead.fileProcessTime + overhead.dirProcessTime;

        StringBuilder sb = new StringBuilder();
        sb.append('\n');
        sb.append("=============================================================\n");
        sb.append("=                     Adjusted Stats                        =\n");
        sb.append("=============================================================\n");
        sb.append("initialization:");
        sb.append("\n    parser init:");
        sb.append("\n        measured: ").append(toMs(parserInitTime));
        sb.append("\n        overhead: ").append(toMs(overhead.parserInitTime));
        sb.append("\n        est:      ").append(toMs(parserInitTime - overhead.parserInitTime));
        sb.append("\n    trie init:   ");
        sb.append("\n        measured: ").append(toMs(trieInitTime));
        sb.append("\n        overhead: ").append(toMs(overhead.trieInitTime));
        sb.append("\n        est:      ").append(toMs(trieInitTime - overhead.parserInitTime));
        sb.append("\nrun time:");
        sb.append("\n    dir processing time:");
        sb.append("\n        measured: ").append(toMs(dirProcessTime));
        sb.append("\n        overhead: ").append(toMs(overhead.dirProcessTime));
        sb.append("\n        est:      ").append(toMs(dirProcessTime - overhead.dirProcessTime));
        sb.append("\n    file processing time:");
        sb.append("\n        measured: ").append(toMs(fileProcessTime));
        sb.append("\n        overhead: ").append(toMs(overhead.fileProcessTime));
        sb.append("\n        est:      ").append(toMs(fileProcessTime - overhead.fileProcessTime));
        sb.append("\n        file read time:");
        sb.append("\n            measured: ").append(toMs(fileReadTime));
        sb.append("\n            overhead: ").append(toMs(overhead.fileReadTime));
        sb.append("\n            est:      ").append(toMs(fileReadTime - overhead.fileReadTime));
        sb.append("\n        file write time:");
        sb.append("\n            measured: ").append(toMs(fileWriteTime));
        sb.append("\n            overhead: ").append(toMs(overhead.fileWriteTime));
        sb.append("\n            est:      ").append(toMs(fileWriteTime - overhead.fileWriteTime));
        sb.append("\n        html generation:");
        sb.append("\n            measured: ").append(toMs(htmlGenTime));
        sb.append("\n            overhead: ").append(toMs(overhead.htmlGenTime));
        sb.append("\n            est:      ").append(toMs(htmlGenTime - overhead.htmlGenTime));
        sb.append("\n        parser:         ");
        sb.append("\n            measured: ").append(toMs(parseTime));
        sb.append("\n            overhead: ").append(toMs(overhead.parseTime));
        sb.append("\n            est:      ").append(toMs(parseTime - overhead.parseTime));
        sb.append("\n            trie match: ");
        sb.append("\n                measured: ").append(toMs(trieMatchTime));
        sb.append("\n                overhead: ").append(toMs(overhead.trieMatchTime));
        sb.append("\n                est:      ").append(toMs(trieMatchTime - overhead.trieMatchTime));
        sb.append("\n            parsing:     ");
        sb.append("\n                measured: ").append(toMs(parseTime - trieMatchTime));
        sb.append("\n                overhead: ").append(toMs(overhead.parseTime - overhead.trieMatchTime));
        sb.append("\n                est:      ").append(toMs(parseTime - trieMatchTime - overhead.parseTime + overhead.trieMatchTime));
        double addtl = toMs(fileProcessTime - htmlGenTime - parseTime - fileReadTime - fileWriteTime);
        double addtlOverhead = toMs(overhead.fileProcessTime - overhead.htmlGenTime - overhead.parseTime -
                overhead.fileReadTime - overhead.fileWriteTime);
        sb.append("\n        other (overhead):");
        sb.append("\n            measured: ").append(addtl);
        sb.append("\n            overhead: ").append(addtlOverhead);
        sb.append("\n            est:      ").append(addtl - addtlOverhead);
        sb.append("\n    other (overhead):").append(toMs(totalTime - dirProcessTime - fileProcessTime));
        sb.append("\n        measured: ").append(toMs(totalTime - dirProcessTime - fileProcessTime));
        sb.append("\n        overhead: ").append(toMs(overhead.totalTime - overhead.dirProcessTime - overhead.fileProcessTime));
        sb.append("\n        est:      ").append(toMs(totalTime - dirProcessTime - fileProcessTime - overhead.totalTime +
                                                    overhead.dirProcessTime + overhead.fileProcessTime));
        sb.append("\ntotal: ").append(toMs(totalTime));
        sb.append("\n    measured: ").append(toMs(totalTime));
        sb.append("\n    overhead: ").append(toMs(overhead.totalTime));
        sb.append("\n    est:      ").append(toMs(totalTime - overhead.totalTime));

        sb.append("\n\nSystem.nanoTime() calls: ").append(nanoCalls);
        sb.append(String.format("\nestimated profiling overhead:\n\t%d * 65ms / 1e6 calls = %f ms\n", nanoCalls, nanoCalls * 65.0f * 1e-6));
        return sb.toString();
    }
}