package io.github.juanjguerrer.meslite.common;

public final class LikeEscaper {
    public static final char ESCAPE_CHAR = '\\';

    private LikeEscaper() { }

    public static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
