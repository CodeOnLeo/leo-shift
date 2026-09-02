package io.github.codeonleo.leoshift.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.codeonleo.leoshift.service.FeedFetcher.FeedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 구독 주소 검증.
 *
 * <p>이 앱은 홈서버에서 돌고, 공유기 관리 페이지 · NAS와 같은 내부망에 있다.
 * 주소를 사용자가 직접 넣으므로, 검증이 없으면 "구독 주소"가 그대로 내부망 읽기
 * 통로가 된다. 여기가 그 방어선이다.
 *
 * <p>네트워크를 타지 않는 것만 확인한다 — {@code validate}는 이름 해석까지만 하고
 * 요청은 보내지 않는다.
 */
class FeedFetcherTest {

    /**
     * 공개 대역의 주소.
     *
     * <p>이름이 아니라 숫자로 적는다. 이름을 쓰면 검증이 DNS를 타서, 네트워크가 없는
     * 곳에서 테스트가 실패한다 — 막는 쪽이 아니라 통과하는 쪽만 그렇게 되므로
     * 조용히 의미가 뒤집힌다.
     */
    private static final String PUBLIC_IP = "93.184.216.34";

    /** 운영 설정. 사설망 차단이 켜져 있다. */
    private final FeedFetcher fetcher = new FeedFetcher(5_242_880, false);

    @ParameterizedTest
    @DisplayName("내부망을 가리키는 주소를 막는다")
    @ValueSource(strings = {
            "http://127.0.0.1/basic.ics",
            "http://localhost:8080/basic.ics",
            "http://10.0.0.5/basic.ics",
            "http://192.168.0.1/basic.ics",
            "http://172.16.0.1/basic.ics",
            "http://169.254.169.254/latest/meta-data",   // 클라우드 메타데이터
            "http://[::1]/basic.ics",
            "http://0.0.0.0/basic.ics",
    })
    void blocksPrivateNetwork(String url) {
        assertThatThrownBy(() -> fetcher.validate(url))
                .isInstanceOf(FeedException.class)
                .hasMessageContaining("내부망");
    }

    @ParameterizedTest
    @DisplayName("http · https가 아닌 주소를 막는다")
    @ValueSource(strings = {
            "file:///etc/passwd",
            "ftp://example.com/basic.ics",
            "gopher://example.com/",
            "jar:file:///tmp/x.zip!/a.ics",
    })
    void blocksOtherSchemes(String url) {
        assertThatThrownBy(() -> fetcher.validate(url)).isInstanceOf(FeedException.class);
    }

    @Test
    @DisplayName("주소에 계정 정보를 붙이는 우회를 막는다")
    void blocksUserInfo() {
        // https://evil.com@127.0.0.1/ 처럼 사람과 파서가 다르게 읽는 주소를 막는다
        assertThatThrownBy(() -> fetcher.validate("https://example.com@127.0.0.1/basic.ics"))
                .isInstanceOf(FeedException.class);
    }

    @Test
    @DisplayName("webcal 주소를 https로 바꿔 받는다")
    void normalizesWebcal() {
        // 구글·애플이 구독 주소로 나눠주는 형태다. 그대로 거절하면 사용자가 손으로 고쳐야 한다.
        assertThat(fetcher.validate("webcal://" + PUBLIC_IP + "/basic.ics").getScheme())
                .isEqualTo("https");
    }

    @Test
    @DisplayName("바깥을 가리키는 주소는 통과시킨다")
    void allowsPublicAddress() {
        assertThatCode(() -> fetcher.validate("https://" + PUBLIC_IP + "/calendar/basic.ics"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("빈 주소와 호스트 없는 주소를 막는다")
    void blocksMalformed() {
        assertThatThrownBy(() -> fetcher.validate("")).isInstanceOf(FeedException.class);
        assertThatThrownBy(() -> fetcher.validate("https:///basic.ics")).isInstanceOf(FeedException.class);
        assertThatThrownBy(() -> fetcher.validate("not a url")).isInstanceOf(FeedException.class);
    }

    @Test
    @DisplayName("사설망 허용을 켜면 통과한다")
    void allowsPrivateWhenConfigured() {
        // 로컬 개발에서 제 컴퓨터의 테스트 피드를 구독해 보기 위한 스위치다.
        // 운영에서 켜면 안 되므로 기본값은 false이고, 이 테스트가 그 의미를 못박는다.
        FeedFetcher local = new FeedFetcher(5_242_880, true);
        assertThatCode(() -> local.validate("http://127.0.0.1:5173/basic.ics"))
                .doesNotThrowAnyException();
    }
}
