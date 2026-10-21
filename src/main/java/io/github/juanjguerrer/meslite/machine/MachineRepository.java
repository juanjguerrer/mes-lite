package io.github.juanjguerrer.meslite.machine;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MachineRepository extends JpaRepository<Machine, Long>, JpaSpecificationExecutor<Machine> {
    Optional<Machine> findByLineCodeAndCode(String lineCode, String code);
}
