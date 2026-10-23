package io.github.juanjguerrer.meslite.line;

import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import io.github.juanjguerrer.meslite.common.query.LikeEscaper;

public final class ProductionLineSpecifications {
    private ProductionLineSpecifications() { }

    public static Specification<ProductionLine> codeEquals(String code) {
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(root.get("code"), normalizedCode);
    }

    public static Specification<ProductionLine> codeContains(String code) {
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        return (root, query, cb) -> cb.like(cb.upper(root.get("code")), "%" + LikeEscaper.escape(normalizedCode.toUpperCase(Locale.ROOT)) + "%", LikeEscaper.ESCAPE_CHAR);
    }

    public static Specification<ProductionLine> nameContains(String name) {
        String normalizedName = name.trim();
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), "%" + LikeEscaper.escape(normalizedName.toLowerCase(Locale.ROOT)) + "%", LikeEscaper.ESCAPE_CHAR);
    }
}
