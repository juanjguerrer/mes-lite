package io.github.juanjguerrer.meslite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MesLiteApplication {
    public static void main(String[] args) {
        SpringApplication.run(MesLiteApplication.class, args);
    }
}
