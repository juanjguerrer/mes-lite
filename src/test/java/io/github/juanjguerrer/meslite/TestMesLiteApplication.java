package io.github.juanjguerrer.meslite;

import org.springframework.boot.SpringApplication;

public class TestMesLiteApplication {

    public static void main(String[] args) {
        SpringApplication.from(MesLiteApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
