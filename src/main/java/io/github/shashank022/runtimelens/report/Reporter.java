package io.github.shashank022.runtimelens.report;

import io.github.shashank022.runtimelens.engine.Analyzer.ScanResult;

public interface Reporter {
    String render(ScanResult result);
}
