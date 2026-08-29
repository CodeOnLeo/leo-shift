package io.github.codeonleo.leoshift.config;

import io.github.codeonleo.leoshift.domain.calendar.Calendar;
import io.github.codeonleo.leoshift.domain.user.User;
import io.github.codeonleo.leoshift.domain.work.ScheduleType;
import io.github.codeonleo.leoshift.domain.work.WorkRule;
import io.github.codeonleo.leoshift.repository.CalendarRepository;
import io.github.codeonleo.leoshift.repository.ScheduleTypeRepository;
import io.github.codeonleo.leoshift.repository.UserRepository;
import io.github.codeonleo.leoshift.repository.WorkRuleRepository;
import io.github.codeonleo.leoshift.schedule.preset.PatternPreset;
import io.github.codeonleo.leoshift.schedule.preset.PatternPresets;
import io.github.codeonleo.leoshift.schedule.preset.ScheduleTypeSpec;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 로컬 개발 전용.
 *
 * <p>아직 로그인 수단이 없어서 {@code /api/**}가 전부 막혀 있다. 로컬에서는
 * 개발용 사용자로 자동 인증해서 화면을 볼 수 있게 한다.
 *
 * <p><b>{@code local} 프로파일에서만 활성화된다.</b> 다른 환경에서는 이 설정이
 * 아예 로딩되지 않으므로, 인증을 만들기 전까지 서버는 계속 잠겨 있다.
 * 이전 구현처럼 조용히 사용자 1번으로 폴백하는 일은 없다.
 */
@Configuration
@Profile("local")
public class LocalDevConfig {

    private static final Logger log = LoggerFactory.getLogger(LocalDevConfig.class);
    private static final String DEV_EMAIL = "dev@localhost";

    @Bean
    SecurityFilterChain localFilterChain(HttpSecurity http, UserRepository userRepository) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(new DevAuthFilter(userRepository), AnonymousAuthenticationFilter.class);
        return http.build();
    }

    /** 개발용 사용자로 SecurityContext를 채운다. */
    private static final class DevAuthFilter extends OncePerRequestFilter {

        private final UserRepository userRepository;

        private DevAuthFilter(UserRepository userRepository) {
            this.userRepository = userRepository;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                userRepository.findByEmail(DEV_EMAIL).ifPresent(user ->
                        SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                        user.getId(), null, AuthorityUtils.createAuthorityList("ROLE_USER"))));
            }
            chain.doFilter(request, response);
        }
    }

    /**
     * 화면을 볼 수 있게 시드 데이터를 만든다. 이미 있으면 아무것도 하지 않는다.
     *
     * <p>프리셋을 실제로 적용해서, 프리셋 → 일정 타입 → 근무 규칙 흐름이
     * 동작하는지 개발 중에 계속 확인되게 한다.
     */
    @Bean
    CommandLineRunner devSeed(UserRepository userRepository,
                              CalendarRepository calendarRepository,
                              ScheduleTypeRepository scheduleTypeRepository,
                              WorkRuleRepository workRuleRepository) {
        return new DevSeeder(userRepository, calendarRepository, scheduleTypeRepository, workRuleRepository);
    }

    /** record가 아니라 일반 클래스다. @Transactional 프록시는 final 클래스를 감쌀 수 없다. */
    static class DevSeeder implements CommandLineRunner {

        private static final String PRESET_ID = "kr.shift.4team3shift";
        private static final String TEAM = "2조";

        private final UserRepository userRepository;
        private final CalendarRepository calendarRepository;
        private final ScheduleTypeRepository scheduleTypeRepository;
        private final WorkRuleRepository workRuleRepository;

        DevSeeder(UserRepository userRepository,
                  CalendarRepository calendarRepository,
                  ScheduleTypeRepository scheduleTypeRepository,
                  WorkRuleRepository workRuleRepository) {
            this.userRepository = userRepository;
            this.calendarRepository = calendarRepository;
            this.scheduleTypeRepository = scheduleTypeRepository;
            this.workRuleRepository = workRuleRepository;
        }

        @Override
        @Transactional
        public void run(String... args) {
            if (userRepository.findByEmail(DEV_EMAIL).isPresent()) {
                return;
            }

            User user = userRepository.save(User.builder()
                    .email(DEV_EMAIL)
                    .name("개발자")
                    .nickname("dev")
                    .colorTag("#2563EB")
                    .build());

            Calendar calendar = calendarRepository.save(Calendar.builder()
                    .ownerUser(user)
                    .name("내 근무")
                    .kind(Calendar.Kind.WORK)
                    .color("#2563EB")
                    .isDefault(true)
                    .build());

            PatternPresets presets = PatternPresets.load();
            PatternPreset preset = presets.require(PRESET_ID);

            int order = 10;
            for (ScheduleTypeSpec spec : presets.scheduleTypesFor(preset)) {
                scheduleTypeRepository.save(ScheduleType.builder()
                        .calendar(calendar)
                        .code(spec.code())
                        .name(spec.name())
                        .color(spec.color())
                        .category(ScheduleType.Category.valueOf(spec.category().name()))
                        .startTime(spec.startTime())
                        .endTime(spec.endTime())
                        .crossesMidnight(spec.crossesMidnight())
                        .halfDay(spec.halfDay())
                        .sortOrder(order)
                        .build());
                order += 10;
            }

            // 이번 달 1일을 기준일로. 프리셋이 조별로 시퀀스를 회전해준다.
            LocalDate anchor = LocalDate.now().withDayOfMonth(1);
            List<String> sequence = preset.sequenceFor(TEAM);

            workRuleRepository.save(WorkRule.builder()
                    .calendar(calendar)
                    .anchorDate(anchor)
                    .cycleLength(sequence.size())
                    .codeSequence(sequence)
                    .effectiveFrom(anchor.minusYears(1))
                    .sourcePresetId(preset.id())
                    .build());

            log.info("개발용 시드 생성: {} / 캘린더 {} / 프리셋 {} {}",
                    DEV_EMAIL, calendar.getId(), preset.name(), TEAM);
        }
    }
}
