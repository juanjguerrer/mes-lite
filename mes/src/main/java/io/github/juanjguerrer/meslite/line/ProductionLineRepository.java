package io.github.juanjguerrer.meslite.line;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductionLineRepository extends JpaRepository<ProductionLine, Long>, JpaSpecificationExecutor<ProductionLine> {
}
