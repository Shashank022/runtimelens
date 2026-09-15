package io.github.shashank022.runtimelens.model;

import java.nio.file.Path;

public record Finding(
        String ruleId,
        String title,
        Severity severity,
        String category,
        Path file,
        int line,
        String message,
        String evidence,
        String suggestion) {
}
