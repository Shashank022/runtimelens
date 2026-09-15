package io.github.shashank022.runtimelens.rules;

import io.github.shashank022.runtimelens.engine.SourceFile;
import io.github.shashank022.runtimelens.model.Finding;
import io.github.shashank022.runtimelens.model.Severity;
import io.github.shashank022.runtimelens.util.SourceUtils;

import java.util.List;

public abstract class AbstractRule implements Rule {
    private final String id;
    private final String title;
    private final Severity severity;
    private final String category;
    private final String explanation;
    private final String suggestion;

    protected AbstractRule(String id, String title, Severity severity, String category,
                           String explanation, String suggestion) {
        this.id = id;
        this.title = title;
        this.severity = severity;
        this.category = category;
        this.explanation = explanation;
        this.suggestion = suggestion;
    }

    @Override public String id() { return id; }
    @Override public String title() { return title; }
    @Override public Severity defaultSeverity() { return severity; }
    @Override public String category() { return category; }
    @Override public String explanation() { return explanation; }
    @Override public String suggestion() { return suggestion; }

    protected Finding finding(SourceFile source, int line, String message) {
        return finding(source, line, message, source.line(line));
    }

    protected Finding finding(SourceFile source, int line, String message, String evidence) {
        return new Finding(
                id, title, severity, category, source.displayPath(), line,
                message, SourceUtils.trimEvidence(evidence), suggestion);
    }

    @Override public abstract List<Finding> analyze(SourceFile source);
}
