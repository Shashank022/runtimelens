package io.github.shashank022.runtimelens.report;

import io.github.shashank022.runtimelens.engine.Analyzer.ScanResult;
import io.github.shashank022.runtimelens.model.Finding;
import io.github.shashank022.runtimelens.model.Severity;

import java.util.EnumMap;
import java.util.Map;

public final class TextReporter implements Reporter {
    @Override
    public String render(ScanResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("RuntimeLens 0.1.0\n");
        sb.append("Scanning: ").append(result.root()).append("\n");
        sb.append("Java files: ").append(result.filesScanned()).append("\n\n");

        if (result.findings().isEmpty()) {
            sb.append("✓ No findings from the enabled RuntimeLens rules.\n");
            return sb.toString();
        }

        for (Finding f : result.findings()) {
            sb.append("────────────────────────────────────────────────────────────\n");
            sb.append(f.severity()).append("  ").append(f.ruleId()).append("  ").append(f.title()).append("\n");
            sb.append(f.file()).append(":").append(f.line()).append("\n\n");
            sb.append(f.message()).append("\n");
            if (f.evidence() != null && !f.evidence().isBlank()) sb.append("  > ").append(f.evidence()).append("\n");
            sb.append("\nSuggestion: ").append(f.suggestion()).append("\n\n");
        }

        Map<Severity, Integer> counts = new EnumMap<>(Severity.class);
        for (Severity s : Severity.values()) counts.put(s, 0);
        for (Finding f : result.findings()) counts.compute(f.severity(), (k, v) -> v + 1);

        int score = Math.max(0, 100
                - counts.get(Severity.CRITICAL) * 25
                - counts.get(Severity.HIGH) * 15
                - counts.get(Severity.MEDIUM) * 7
                - counts.get(Severity.LOW) * 2);

        sb.append("════════════════════════════════════════════════════════════\n");
        sb.append("Summary\n");
        sb.append("Critical: ").append(counts.get(Severity.CRITICAL)).append("\n");
        sb.append("High:     ").append(counts.get(Severity.HIGH)).append("\n");
        sb.append("Medium:   ").append(counts.get(Severity.MEDIUM)).append("\n");
        sb.append("Low:      ").append(counts.get(Severity.LOW)).append("\n");
        sb.append("Score:    ").append(score).append("/100\n");
        if (result.unreadableFiles() > 0) sb.append("Unreadable files: ").append(result.unreadableFiles()).append("\n");
        if (result.analysisErrors() > 0) sb.append("Rule analysis errors: ").append(result.analysisErrors()).append("\n");
        return sb.toString();
    }
}
