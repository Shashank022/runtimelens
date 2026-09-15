package io.github.shashank022.runtimelens.engine;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public final class SourceFile {
    private final Path absolutePath;
    private final Path displayPath;
    private final String text;
    private final List<String> lines;

    public SourceFile(Path absolutePath, Path displayPath, String text) {
        this.absolutePath = absolutePath;
        this.displayPath = displayPath;
        this.text = text;
        this.lines = Arrays.asList(text.split("\\R", -1));
    }

    public Path absolutePath() { return absolutePath; }
    public Path displayPath() { return displayPath; }
    public String text() { return text; }
    public List<String> lines() { return lines; }

    public String line(int oneBasedLine) {
        if (oneBasedLine < 1 || oneBasedLine > lines.size()) return "";
        return lines.get(oneBasedLine - 1);
    }

    public int lineOfOffset(int offset) {
        int safe = Math.max(0, Math.min(offset, text.length()));
        int line = 1;
        for (int i = 0; i < safe; i++) if (text.charAt(i) == '\n') line++;
        return line;
    }
}
