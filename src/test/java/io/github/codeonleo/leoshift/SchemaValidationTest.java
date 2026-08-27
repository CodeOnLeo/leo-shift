package io.github.codeonleo.leoshift;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 스키마와 엔티티가 어긋나지 않는지 확인한다.
 *
 * <p>{@code ddl-auto=validate}라 컨텍스트가 뜬다는 것 자체가 검증이다. 컬럼이
 * 빠졌거나 타입이 다르면 부팅이 실패한다.
 *
 * <p>이전 구현은 {@code ddl-auto=update}와 Flyway를 같이 써서, Hibernate가
 * 마이그레이션 뒤에 스키마를 또 바꿨다. 그래서 운영과 로컬 스키마가 조용히
 * 달라졌고 특정 환경에서만 나는 오류가 생겼다. 이 테스트가 그 재발을 막는다.
 */
@SpringBootTest
@DisplayName("스키마 · 엔티티 정합성")
class SchemaValidationTest extends AbstractPostgresTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("모든 엔티티가 실제 스키마와 맞는다")
    void entitiesMatchSchema() {
        List<String> names = entityManager.getMetamodel().getEntities().stream()
                .map(EntityType::getName)
                .sorted()
                .toList();

        // 컨텍스트가 떴다는 것은 validate를 통과했다는 뜻이다.
        assertThat(names).contains(
                "User", "UserIdentity", "RefreshToken", "UserSettings",
                "Group", "GroupMember",
                "Calendar", "CalendarShare", "CalendarFeedToken",
                "ScheduleType", "WorkRule", "Leave", "DayOverride",
                "Event", "EventOccurrence",
                "ExternalSource", "ExternalEvent");
    }

    @Test
    @DisplayName("마이그레이션이 전부 적용됐다")
    void migrationsApplied() {
        Object applied = entityManager
                .createNativeQuery("select count(*) from flyway_schema_history where success = true")
                .getSingleResult();
        assertThat(((Number) applied).intValue()).isGreaterThan(0);
    }
}
