package io.github.juanjguerrer.meslite.shift;

import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import io.github.juanjguerrer.meslite.common.LikeEscaper;

public final class ShiftSpecifications {
    private ShiftSpecifications() {
    }

    public static Specification<Shift> codeEquals(String code) {
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(root.get("code"), normalizedCode);
    }

    public static Specification<Shift> codeContains(String code) {
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        return (root, query, cb) -> cb.like(cb.upper(root.get("code")), "%" + LikeEscaper.escape(normalizedCode.toUpperCase(Locale.ROOT)) + "%", LikeEscaper.ESCAPE_CHAR);
    }

    public static Specification<Shift> nameContains(String name) {
        String normalizedName = name.trim();
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), "%" + LikeEscaper.escape(normalizedName.toLowerCase(Locale.ROOT)) + "%", LikeEscaper.ESCAPE_CHAR);
    }

    public static Specification<Shift> lineEquals(Long lineId) {
        return (root, query, cb) -> cb.equal(root.get("line").get("id"), lineId);
    }
}
