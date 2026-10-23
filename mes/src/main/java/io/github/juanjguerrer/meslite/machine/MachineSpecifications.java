package io.github.juanjguerrer.meslite.machine;

import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import io.github.juanjguerrer.meslite.common.LikeEscaper;

public final class MachineSpecifications {
    private MachineSpecifications() { }

    public static Specification<Machine> codeEquals(String code) {
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(root.get("code"), normalizedCode);
    }

    public static Specification<Machine> codeContains(String code) {
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        return (root, query, cb) -> cb.like(cb.upper(root.get("code")), "%" + LikeEscaper.escape(normalizedCode.toUpperCase(Locale.ROOT)) + "%", LikeEscaper.ESCAPE_CHAR);
    }

    public static Specification<Machine> nameContains(String name) {
        String normalizedName = name.trim();
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), "%" + LikeEscaper.escape(normalizedName.toLowerCase(Locale.ROOT)) + "%", LikeEscaper.ESCAPE_CHAR);
    }

    public static Specification<Machine> lineEquals(Long lineId) {
        return (root, query, cb) -> cb.equal(root.get("line").get("id"), lineId);
    }
}
