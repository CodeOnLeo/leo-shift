package io.github.codeonleo.leoshift;

import io.github.codeonleo.leoshift.config.PushProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(PushProperties.class)
public class LeoShiftApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeoShiftApplication.class, args);
    }

}
