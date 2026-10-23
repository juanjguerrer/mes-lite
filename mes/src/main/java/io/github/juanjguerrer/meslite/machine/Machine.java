package io.github.juanjguerrer.meslite.machine;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import io.github.juanjguerrer.meslite.line.ProductionLine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "machines")
public class Machine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String code;

    private String description;
    private Integer idealCycleTimeMs;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "line_id")
    private ProductionLine line;

    public Machine(String name, String code, String description, Integer idealCycleTimeMs, ProductionLine line) {
        this.name = name;
        this.code = code;
        this.description = description;
        this.idealCycleTimeMs = idealCycleTimeMs;
        this.line = line;
    }

    protected Machine() {
        // Default constructor for JPA
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public Integer getIdealCycleTimeMs() {
        return idealCycleTimeMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public ProductionLine getLine() {
        return line;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setIdealCycleTimeMs(Integer idealCycleTimeMs) {
        this.idealCycleTimeMs = idealCycleTimeMs;
    }
}
