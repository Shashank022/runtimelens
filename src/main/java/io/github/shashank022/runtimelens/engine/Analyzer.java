package io.github.shashank022.runtimelens.engine;

import io.github.shashank022.runtimelens.model.Finding;
import io.github.shashank022.runtimelens.rules.Rule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public final class Analyzer {
    private static final Set<String> IGNORED_DIRS = Set.of(
            ".git", ".idea", ".gradle", ".mvn", "target", "build", "out", "node_modules", "dist");

    private final List<Rule> rules;

    public Analyzer(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public ScanResult scan(Path root) throws IOException {
        Path absoluteRoot = root.toAbsolutePath().normalize();
        if (!Files.exists(absoluteRoot)) throw new NoSuchFileException(absoluteRoot.toString());

        List<Path> javaFiles = new ArrayList<>();
        if (Files.isRegularFile(absoluteRoot)) {
            if (absoluteRoot.toString().endsWith(".java")) javaFiles.add(absoluteRoot);
        } else {
            try (Stream<Path> stream = Files.walk(absoluteRoot)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> !isIgnored(absoluteRoot, p))
                        .forEach(javaFiles::add);
            }
        }

        javaFiles.sort(Comparator.naturalOrder());
        List<Finding> findings = new ArrayList<>();
        int unreadable = 0;
        int analysisErrors = 0;

        for (Path file : javaFiles) {
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                Path display = Files.isDirectory(absoluteRoot) ? absoluteRoot.relativize(file) : file.getFileName();
                SourceFile source = new SourceFile(file, display, text);
                for (Rule rule : rules) {
                    try {
                        findings.addAll(rule.analyze(source));
                    } catch (RuntimeException e) {
                        analysisErrors++;
                    }
                }
            } catch (IOException e) {
                unreadable++;
            }
        }

        findings.sort(Comparator
                .comparing((Finding f) -> f.file().toString())
                .thenComparingInt(Finding::line)
                .thenComparing(Finding::ruleId));

        return new ScanResult(absoluteRoot, javaFiles.size(), unreadable, analysisErrors, findings);
    }

    private static boolean isIgnored(Path root, Path file) {
        Path relative = root.relativize(file);
        for (Path part : relative) if (IGNORED_DIRS.contains(part.toString())) return true;
        return false;
    }

    public record ScanResult(Path root, int filesScanned, int unreadableFiles, int analysisErrors, List<Finding> findings) {
        public ScanResult { findings = List.copyOf(findings); }
    }
}
