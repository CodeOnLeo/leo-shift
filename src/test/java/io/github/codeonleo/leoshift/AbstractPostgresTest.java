package io.github.codeonleo.leoshift;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 진짜 PostgreSQL로 도는 테스트의 공통 기반.
 *
 * <p>H2로는 부족하다. 스키마가 배제 제약({@code btree_gist}), 부분 인덱스,
 * {@code jsonb}, 복합 FK의 {@code ON UPDATE CASCADE}를 쓰기 때문이다.
 *
 * <p>컨테이너를 <b>{@code @Container} 없이 직접 띄운다.</b> {@code @Container}는
 * 클래스가 끝날 때 컨테이너를 멈추는데, 정적 필드는 하위 클래스들이 공유하므로
 * 먼저 끝난 클래스가 멈춘 컨테이너를 다음 클래스가 물려받아 연결이 거부된다.
 * 테스트 클래스가 하나일 때는 드러나지 않다가 두 번째가 생기는 순간 터진다.
 * 정지는 Testcontainers의 Ryuk가 JVM 종료 시에 맡는다.
 */
@Tag("integration")
public abstract class AbstractPostgresTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // 운영과 같은 설정으로 검증한다. Hibernate가 스키마를 바꾸지 않고,
        // Flyway가 만든 스키마와 엔티티가 맞는지 확인만 한다.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration-next");
        registry.add("spring.flyway.baseline-on-migrate", () -> "false");

        // Colima 같은 VM 기반 런타임은 컨테이너가 뜬 직후 호스트 쪽 포트 포워딩이
        // 잠깐 늦을 수 있다. 첫 연결에 실패해도 몇 번 다시 시도한다.
        registry.add("spring.flyway.connect-retries", () -> "5");
        registry.add("spring.flyway.connect-retries-interval", () -> "2");
    }
}
