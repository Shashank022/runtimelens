package io.github.shashank022.runtimelens.rules;

import io.github.shashank022.runtimelens.engine.SourceFile;
import io.github.shashank022.runtimelens.model.Finding;
import io.github.shashank022.runtimelens.model.Severity;

import java.util.List;

public interface Rule {
    String id();
    String title();
    String category();
    Severity defaultSeverity();
    String explanation();
    String suggestion();
    List<Finding> analyze(SourceFile source);
}
