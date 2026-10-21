package io.github.juanjguerrer.meslite.line;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.juanjguerrer.meslite.common.CodeRules;
import io.github.juanjguerrer.meslite.common.LogicalOperator;
import io.github.juanjguerrer.meslite.common.NotFoundException;
import io.github.juanjguerrer.meslite.line.dto.CreateLineRequest;
import io.github.juanjguerrer.meslite.line.dto.LineResponse;
import io.github.juanjguerrer.meslite.line.dto.UpdateLineRequest;

@Service
public class ProductionLineService {
    private final ProductionLineRepository repository;

    public ProductionLineService(ProductionLineRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public LineResponse create(CreateLineRequest request) {
        String code = CodeRules.normalizeCode(request.code());
        ProductionLine productionLine = new ProductionLine(request.name(), code, request.description());
        return LineResponse.from(repository.save(productionLine));
    }

    @Transactional(readOnly = true)
    public LineResponse findById(Long id) {
        return repository.findById(id).map(LineResponse::from)
            .orElseThrow(() -> NotFoundException.byId("Production line", id));
    }

    @Transactional
    public LineResponse update(Long id, UpdateLineRequest request) {
        ProductionLine productionLine = repository.findById(id)
            .orElseThrow(() -> NotFoundException.byId("Production line", id));
        productionLine.setName(request.name());
        productionLine.setDescription(request.description());
        return LineResponse.from(repository.saveAndFlush(productionLine));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw NotFoundException.byId("Production line", id);
        }
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Page<LineResponse> list(Pageable pageable, String code, String name, LogicalOperator operator) {
        List<Specification<ProductionLine>> specs = new ArrayList<>();

        if (code != null && !code.isBlank()) {
            Specification<ProductionLine> codeSpec = ProductionLineSpecifications.codeContains(code);
            specs.add(codeSpec);
        }
        if (name != null && !name.isBlank()) {
            Specification<ProductionLine> nameSpec = ProductionLineSpecifications.nameContains(name);
            specs.add(nameSpec);
        }
        if (specs.isEmpty()) {
            return repository.findAll(pageable).map(LineResponse::from);
        }
        Specification<ProductionLine> spec = operator == LogicalOperator.OR
            ? Specification.anyOf(specs)
            : Specification.allOf(specs);
        return repository.findAll(spec, pageable).map(LineResponse::from);
    }
}
