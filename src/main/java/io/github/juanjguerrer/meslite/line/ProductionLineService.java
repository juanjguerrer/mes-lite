package io.github.juanjguerrer.meslite.line;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.juanjguerrer.meslite.common.CodeRules;
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
        .orElseThrow(() -> new NotFoundException("Production line", id));
    }

    @Transactional
    public LineResponse update(Long id, UpdateLineRequest request) {
        ProductionLine productionLine = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Production line", id));
        productionLine.setName(request.name());
        productionLine.setDescription(request.description());
        return LineResponse.from(repository.saveAndFlush(productionLine));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("Production line", id);
        }
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Page<LineResponse> list(Pageable pageable) {
        return repository.findAll(pageable).map(LineResponse::from);
    }
}
