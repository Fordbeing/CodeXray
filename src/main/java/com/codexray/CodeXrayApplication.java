package com.codexray;

import com.codexray.config.DotEnvConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.codexray.mapper")
public class CodeXrayApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(CodeXrayApplication.class);
        app.addListeners(new DotEnvConfig());
        app.run(args);
    }
}
