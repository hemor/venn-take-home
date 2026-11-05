package com.emmanuel.venn.loads.batch;

public record BatchSummary(int linesRead, int responsesWritten, int duplicatesIgnored, int invalidLines) {}
