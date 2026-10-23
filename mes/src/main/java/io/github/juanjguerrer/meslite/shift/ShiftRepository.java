package io.github.juanjguerrer.meslite.shift;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ShiftRepository extends JpaRepository<Shift, Long>, JpaSpecificationExecutor<Shift> {
    List<Shift> findByLineIdOrderByStartTime(Long lineId);
    Optional<Shift> findByLineIdAndCode(Long lineId, String code);
}
