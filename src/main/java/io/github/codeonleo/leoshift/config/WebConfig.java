package io.github.codeonleo.leoshift.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SPA 라우트를 index.html로 넘긴다.
 *
 * <p>이게 없으면 {@code /month/2026/3}을 새로고침할 때 404가 난다.
 * 클라이언트 라우팅을 쓰는 이상 서버가 모르는 경로를 앱 진입점으로 보내야 한다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 점이 없는 한 단계 경로 (/month, /week)
        registry.addViewController("/{path:[^\\.]*}").setViewName("forward:/index.html");
        // 두 단계 (/day/2026-03-10, /groups/1)
        registry.addViewController("/{path:^(?!api|assets|icons).*}/{sub:[^\\.]*}")
                .setViewName("forward:/index.html");
        // 세 단계 (/month/2026/3)
        registry.addViewController("/{path:^(?!api|assets|icons).*}/{sub:[^\\.]*}/{sub2:[^\\.]*}")
                .setViewName("forward:/index.html");
    }
}
