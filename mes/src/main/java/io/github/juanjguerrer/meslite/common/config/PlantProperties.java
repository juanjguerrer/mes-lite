package io.github.juanjguerrer.meslite.common.config;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mes.plant")
public record PlantProperties(ZoneId zoneId) { }
