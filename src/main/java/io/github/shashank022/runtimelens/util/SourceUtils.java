package io.github.shashank022.runtimelens.util;

import io.github.shashank022.runtimelens.engine.SourceFile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SourceUtils {
    private SourceUtils() {}

    public record BlockRange(int startLine, int endLine) {}

    public record MethodRange(
            String name,
            int declarationLine,
            int startLine,
            int endLine,
            String declaration,
            List<String> annotations) {
        public boolean hasAnnotation(String simpleName) {
            return annotations.stream().anyMatch(a -> a.equals(simpleName) || a.endsWith("." + simpleName));
        }
    }

    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "^(?!\\s*(?:if|for|while|switch|catch|return|new)\\b)" +
            "\\s*(?:public|protected|private|static|final|synchronized|abstract|native|default|strictfp|\\s)*" +
            "(?:<[^>]+>\\s*)?" +
            "[\\w$.<>?,\\[\\]\\s]+\\s+" +
            "([A-Za-z_$][\\w$]*)\\s*\\([^;]*\\)\\s*(?:throws\\s+[^\\{]+)?\\{\\s*$");

    public static List<BlockRange> loopBlocks(SourceFile source) {
        List<BlockRange> ranges = new ArrayList<>();
        List<String> lines = source.lines();
        Pattern loopStart = Pattern.compile("\\b(for|while)\\s*\\(|\\bdo\\s*\\{");

        for (int i = 0; i < lines.size(); i++) {
            String stripped = stripStringsAndLineComment(lines.get(i));
            if (!loopStart.matcher(stripped).find()) continue;

            int braceLine = i;
            int firstBrace = stripped.indexOf('{');
            while (firstBrace < 0 && braceLine + 1 < lines.size() && braceLine - i < 4) {
                braceLine++;
                stripped = stripStringsAndLineComment(lines.get(braceLine));
                firstBrace = stripped.indexOf('{');
            }

            if (firstBrace < 0) {
                ranges.add(new BlockRange(i + 1, Math.min(lines.size(), i + 2)));
                continue;
            }

            int depth = 0;
            boolean started = false;
            outer:
            for (int j = braceLine; j < lines.size(); j++) {
                String clean = stripStringsAndLineComment(lines.get(j));
                for (int k = 0; k < clean.length(); k++) {
                    char c = clean.charAt(k);
                    if (c == '{') { depth++; started = true; }
                    else if (c == '}') {
                        depth--;
                        if (started && depth == 0) {
                            ranges.add(new BlockRange(i + 1, j + 1));
                            break outer;
                        }
                    }
                }
            }
        }
        return ranges;
    }

    public static List<MethodRange> methods(SourceFile source) {
        List<MethodRange> result = new ArrayList<>();
        List<String> lines = source.lines();
        List<String> pendingAnnotations = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.startsWith("@")) {
                String annotation = trimmed.substring(1);
                int paren = annotation.indexOf('(');
                if (paren >= 0) annotation = annotation.substring(0, paren);
                int space = annotation.indexOf(' ');
                if (space >= 0) annotation = annotation.substring(0, space);
                pendingAnnotations.add(annotation.trim());
                continue;
            }

            if (trimmed.isBlank() || trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
                continue;
            }

            String declaration = collectDeclaration(lines, i);
            Matcher matcher = METHOD_PATTERN.matcher(declaration.trim());
            if (!matcher.matches()) {
                if (!trimmed.isBlank() && !trimmed.startsWith("//") &&
                    !trimmed.startsWith("/*") && !trimmed.startsWith("*")) {
                    pendingAnnotations.clear();
                }
                continue;
            }

            String name = matcher.group(1);
            int end = findMatchingBrace(lines, i);
            result.add(new MethodRange(
                    name, i + 1, i + 1, end + 1,
                    declaration.trim(), List.copyOf(pendingAnnotations)));
            pendingAnnotations.clear();
        }
        return result;
    }

    private static String collectDeclaration(List<String> lines, int start) {
        StringBuilder sb = new StringBuilder();
        int max = Math.min(lines.size(), start + 5);
        for (int i = start; i < max; i++) {
            String clean = stripStringsAndLineComment(lines.get(i)).trim();
            if (clean.isBlank()) continue;
            sb.append(' ').append(clean);
            if (clean.contains("{") || clean.endsWith(";")) break;
        }
        return sb.toString();
    }

    private static int findMatchingBrace(List<String> lines, int startLineIndex) {
        int depth = 0;
        boolean started = false;
        for (int i = startLineIndex; i < lines.size(); i++) {
            String clean = stripStringsAndLineComment(lines.get(i));
            for (int j = 0; j < clean.length(); j++) {
                char c = clean.charAt(j);
                if (c == '{') { depth++; started = true; }
                else if (c == '}') {
                    depth--;
                    if (started && depth == 0) return i;
                }
            }
        }
        return lines.size() - 1;
    }

    public static int countTopLevelArguments(String invocationText) {
        int open = invocationText.indexOf('(');
        if (open < 0) return 0;
        int depthParen = 0, depthAngle = 0, depthBrace = 0, depthBracket = 0, commas = 0;
        boolean hasContent = false, inString = false, escaped = false;
        char quote = 0;

        for (int i = open + 1; i < invocationText.length(); i++) {
            char c = invocationText.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) inString = false;
                continue;
            }
            if (c == '"' || c == '\'') { inString = true; quote = c; hasContent = true; continue; }
            if (c == '(') depthParen++;
            else if (c == ')') { if (depthParen == 0) break; depthParen--; }
            else if (c == '<') depthAngle++;
            else if (c == '>') depthAngle = Math.max(0, depthAngle - 1);
            else if (c == '{') depthBrace++;
            else if (c == '}') depthBrace = Math.max(0, depthBrace - 1);
            else if (c == '[') depthBracket++;
            else if (c == ']') depthBracket = Math.max(0, depthBracket - 1);
            else if (!Character.isWhitespace(c)) hasContent = true;

            if (c == ',' && depthParen == 0 && depthAngle == 0 && depthBrace == 0 && depthBracket == 0) commas++;
        }
        return hasContent ? commas + 1 : 0;
    }

    public static String invocationFromOffset(String text, int callOffset) {
        int open = text.indexOf('(', callOffset);
        if (open < 0) return "";
        int depth = 0;
        boolean inString = false, escaped = false;
        char quote = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) inString = false;
                continue;
            }
            if (c == '"' || c == '\'') { inString = true; quote = c; continue; }
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return text.substring(callOffset, i + 1);
            }
        }
        return text.substring(callOffset, Math.min(text.length(), open + 200));
    }

    public static String stripStringsAndLineComment(String line) {
        StringBuilder sb = new StringBuilder();
        boolean inString = false, escaped = false;
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) inString = false;
                sb.append(' ');
                continue;
            }
            if (c == '"' || c == '\'') { inString = true; quote = c; sb.append(' '); continue; }
            if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') break;
            sb.append(c);
        }
        return sb.toString();
    }

    public static String trimEvidence(String line) {
        String s = line.trim();
        return s.length() <= 160 ? s : s.substring(0, 157) + "...";
    }
}
