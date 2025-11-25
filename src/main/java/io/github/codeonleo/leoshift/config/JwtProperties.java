package io.github.codeonleo.leoshift.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {
    private String secret;
    private Long expiration; // 액세스 토큰 만료 시간 (밀리초)
    private Long refreshExpiration; // 리프레시 토큰 만료 시간 (밀리초)
}
