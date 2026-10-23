package io.github.juanjguerrer.meslite.line;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import io.github.juanjguerrer.meslite.TestcontainersConfiguration;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ProductionLineRepositoryTest {

    @Autowired
    private ProductionLineRepository repository;

    @Test
    void savesAndReadsALine() {
        ProductionLine saved = repository.saveAndFlush(new ProductionLine("Assembly 1", "LINE-01", "Main assembly line"));

        ProductionLine found = repository.findById(saved.getId()).orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getName()).isEqualTo("Assembly 1");
        assertThat(found.getCode()).isEqualTo("LINE-01");
        assertThat(found.getDescription()).isEqualTo("Main assembly line");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void rejectsDuplicateCode() {
        repository.saveAndFlush(new ProductionLine("Assembly 1", "LINE-01", null));

        assertThatThrownBy(() -> repository.saveAndFlush(new ProductionLine("Assembly 2", "LINE-01", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
