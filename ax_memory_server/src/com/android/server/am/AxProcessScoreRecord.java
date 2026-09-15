package com.android.server.am;

public final class AxProcessScoreRecord {
    public int pid;
    public int adj;
    public long rss;
    public float score;
    public String processName;

    public AxProcessScoreRecord(int pid, int adj, long rss, String processName) {
        this.pid = pid;
        this.adj = adj;
        this.rss = rss;
        this.score = 0.0f;
        this.processName = processName;
    }
}
