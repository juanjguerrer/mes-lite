package io.github.juanjguerrer.meslite.line;

import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

public final class ProductionLineSpecifications {
    private static final char ESCAPE = '\\';

    private ProductionLineSpecifications() { }

    public static Specification<ProductionLine> codeEquals(String code) {
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(root.get("code"), normalizedCode);
    }

    public static Specification<ProductionLine> codeContains(String code) {
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        return (root, query, cb) -> cb.like(cb.upper(root.get("code")), "%" + escapeLike(normalizedCode.toUpperCase(Locale.ROOT)) + "%", ESCAPE);
    }

    public static Specification<ProductionLine> nameContains(String name) {
        String normalizedName = name.trim();
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), "%" + escapeLike(normalizedName.toLowerCase(Locale.ROOT)) + "%", ESCAPE);
    }

    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
