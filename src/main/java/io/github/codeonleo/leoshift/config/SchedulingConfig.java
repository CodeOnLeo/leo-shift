package io.github.codeonleo.leoshift.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 주기 작업을 켠다. 지금은 외부 캘린더 구독 동기화 하나다.
 *
 * <p><b>인스턴스를 여러 개 띄우면 각자 돌린다.</b> 홈서버 한 대 전제라 지금은 문제가
 * 없지만, 늘리게 되면 잠금이 필요하다 — 같은 피드를 동시에 가져와 캐시를 지우고
 * 넣으면 조회가 잠깐 빈 결과를 본다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
