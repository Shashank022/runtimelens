package io.github.shashank022.runtimelens.model;

public enum Severity {
    CRITICAL(4, "error"),
    HIGH(3, "error"),
    MEDIUM(2, "warning"),
    LOW(1, "note");

    private final int rank;
    private final String sarifLevel;

    Severity(int rank, String sarifLevel) {
        this.rank = rank;
        this.sarifLevel = sarifLevel;
    }

    public int rank() {
        return rank;
    }

    public String sarifLevel() {
        return sarifLevel;
    }

    public static Severity parse(String value) {
        return Severity.valueOf(value.trim().toUpperCase());
    }
}
