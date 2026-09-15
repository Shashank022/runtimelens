package io.github.shashank022.runtimelens;

import io.github.shashank022.runtimelens.engine.Analyzer;
import io.github.shashank022.runtimelens.engine.Analyzer.ScanResult;
import io.github.shashank022.runtimelens.model.Finding;
import io.github.shashank022.runtimelens.model.Severity;
import io.github.shashank022.runtimelens.report.*;
import io.github.shashank022.runtimelens.rules.Rule;
import io.github.shashank022.runtimelens.rules.RuleCatalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class Main {
    public static final String VERSION = "0.1.0";

    public static void main(String[] args) {
        int exit = new Main().run(args);
        if (exit != 0) System.exit(exit);
    }

    int run(String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0]) || "--help".equalsIgnoreCase(args[0]) || "-h".equalsIgnoreCase(args[0])) {
            printHelp();
            return 0;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "scan" -> runScan(Arrays.copyOfRange(args, 1, args.length));
            case "rules" -> runRules();
            case "explain" -> runExplain(Arrays.copyOfRange(args, 1, args.length));
            case "version", "--version", "-v" -> { System.out.println("RuntimeLens " + VERSION); yield 0; }
            default -> {
                System.err.println("Unknown command: " + args[0]);
                printHelp();
                yield 64;
            }
        };
    }

    private int runScan(String[] args) {
        Path path = Path.of(".");
        String format = "text";
        Path output = null;
        Severity failOn = null;
        Set<String> enabledRuleIds = new LinkedHashSet<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--format=")) format = arg.substring("--format=".length());
            else if ("--format".equals(arg) && i + 1 < args.length) format = args[++i];
            else if (arg.startsWith("--output=")) output = Path.of(arg.substring("--output=".length()));
            else if ("--output".equals(arg) && i + 1 < args.length) output = Path.of(args[++i]);
            else if (arg.startsWith("--fail-on=")) failOn = Severity.parse(arg.substring("--fail-on=".length()));
            else if ("--fail-on".equals(arg) && i + 1 < args.length) failOn = Severity.parse(args[++i]);
            else if (arg.startsWith("--rules=")) addRules(enabledRuleIds, arg.substring("--rules=".length()));
            else if ("--rules".equals(arg) && i + 1 < args.length) addRules(enabledRuleIds, args[++i]);
            else if (!arg.startsWith("-")) path = Path.of(arg);
            else {
                System.err.println("Unknown scan option: " + arg);
                return 64;
            }
        }

        List<Rule> rules = RuleCatalog.defaultRules();
        if (!enabledRuleIds.isEmpty()) {
            rules = rules.stream().filter(r -> enabledRuleIds.contains(r.id().toUpperCase(Locale.ROOT))).toList();
        }

        Reporter reporter = switch (format.toLowerCase(Locale.ROOT)) {
            case "text" -> new TextReporter();
            case "json" -> new JsonReporter();
            case "sarif" -> new SarifReporter();
            default -> null;
        };
        if (reporter == null) {
            System.err.println("Unsupported format: " + format + " (use text, json, or sarif)");
            return 64;
        }

        try {
            ScanResult result = new Analyzer(rules).scan(path);
            String rendered = reporter.render(result);
            if (output != null) {
                Path parent = output.toAbsolutePath().getParent();
                if (parent != null) Files.createDirectories(parent);
                Files.writeString(output, rendered, StandardCharsets.UTF_8);
                if ("text".equalsIgnoreCase(format)) System.out.println("RuntimeLens report written to " + output.toAbsolutePath());
            } else {
                System.out.print(rendered);
            }

            if (failOn != null) {
                int thresholdRank = failOn.rank();
                boolean thresholdReached = result.findings().stream()
                        .map(Finding::severity)
                        .anyMatch(s -> s.rank() >= thresholdRank);
                if (thresholdReached) return 2;
            }
            return 0;
        } catch (IOException e) {
            System.err.println("Scan failed: " + e.getMessage());
            return 1;
        }
    }

    private static void addRules(Set<String> ids, String raw) {
        for (String id : raw.split(",")) if (!id.isBlank()) ids.add(id.trim().toUpperCase(Locale.ROOT));
    }

    private int runRules() {
        System.out.printf("%-8s %-8s %-14s %s%n", "RULE", "SEVERITY", "CATEGORY", "TITLE");
        for (Rule rule : RuleCatalog.defaultRules()) {
            System.out.printf("%-8s %-8s %-14s %s%n",
                    rule.id(), rule.defaultSeverity(), rule.category(), rule.title());
        }
        return 0;
    }

    private int runExplain(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: runtimelens explain RL1004");
            return 64;
        }
        Optional<Rule> match = RuleCatalog.byId(args[0]);
        if (match.isEmpty()) {
            System.err.println("Unknown rule: " + args[0]);
            return 64;
        }
        Rule rule = match.get();
        System.out.println(rule.id() + " — " + rule.title());
        System.out.println("Severity: " + rule.defaultSeverity());
        System.out.println("Category: " + rule.category());
        System.out.println("\nWhy it matters\n" + rule.explanation());
        System.out.println("\nRecommended direction\n" + rule.suggestion());
        return 0;
    }

    private void printHelp() {
        System.out.println("""
                RuntimeLens 0.1.0
                Find Java problems that compile successfully but hurt at runtime.

                Usage:
                  java -jar runtimelens.jar scan [path] [options]
                  java -jar runtimelens.jar rules
                  java -jar runtimelens.jar explain <RULE_ID>
                  java -jar runtimelens.jar version

                Scan options:
                  --format text|json|sarif   Output format (default: text)
                  --output <file>            Write the report to a file
                  --fail-on HIGH             Exit 2 when severity threshold is reached
                  --rules RL1001,RL1004      Run only selected rules
                """);
    }
}
