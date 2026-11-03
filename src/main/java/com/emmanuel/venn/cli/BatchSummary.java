package com.emmanuel.venn.cli;

public record BatchSummary(int linesRead, int responsesWritten, int duplicatesIgnored, int invalidLines) {}
