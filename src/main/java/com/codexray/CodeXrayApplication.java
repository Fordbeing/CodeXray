package com.codexray;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.codexray.mapper")
public class CodeXrayApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeXrayApplication.class, args);
    }
}
