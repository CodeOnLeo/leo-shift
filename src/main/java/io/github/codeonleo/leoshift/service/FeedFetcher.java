package io.github.codeonleo.leoshift.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 외부 ICS 피드를 가져온다.
 *
 * <p><b>이 클래스의 존재 이유는 SSRF 차단이다.</b> 사용자가 주소를 직접 넣는데,
 * 이 앱은 홈서버에서 돌아 공유기 관리 페이지 · NAS · 다른 컨테이너와 같은 내부망에
 * 있다. 검증 없이 요청을 보내면 "구독 주소"가 그대로 내부망 읽기 통로가 된다.
 *
 * <p>막는 것은 넷이다.
 * <ul>
 *   <li>사설 · 루프백 · 링크로컬 대역으로의 요청</li>
 *   <li>리다이렉트로 우회하는 것 — 직접 따라가며 매 홉을 다시 검증한다</li>
 *   <li>응답 크기 — 무한 스트림 하나로 힙을 밀어낼 수 있다</li>
 *   <li>느린 응답 — 연결과 읽기 모두에 시한을 둔다</li>
 * </ul>
 *
 * <p>남은 위험은 DNS 리바인딩이다. 검증한 뒤 실제 연결까지의 사이에 이름이 다른
 * 주소로 바뀔 수 있다. 이름 해석 결과를 고정한 채로 연결하려면 소켓을 직접 다뤄야
 * 하는데, 사설망 차단만으로도 실제 위험은 크게 줄어서 여기까지만 한다.
 */
@Component
public class FeedFetcher {

    private static final Logger log = LoggerFactory.getLogger(FeedFetcher.class);

    private static final int MAX_REDIRECTS = 3;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient client;
    private final int maxBytes;
    private final boolean allowPrivate;

    public FeedFetcher(
            @Value("${leoshift.feed.max-bytes:5242880}") int maxBytes,
            // 로컬 개발에서 제 컴퓨터의 테스트 피드를 구독해 보려면 열어야 한다.
            // 운영에서 켜면 안 된다.
            @Value("${leoshift.feed.allow-private-network:false}") boolean allowPrivate) {
        this.maxBytes = maxBytes;
        this.allowPrivate = allowPrivate;
        this.client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                // 자동 추적을 끄고 직접 따라간다. 리다이렉트 대상은 다시 검증해야 한다.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** 주소가 쓸 만한지만 본다. 저장 전에 부르면 잘못된 주소가 DB에 남지 않는다. */
    public URI validate(String rawUrl) {
        URI uri = normalize(rawUrl);
        requirePublic(uri);
        return uri;
    }

    /**
     * 피드 본문을 가져온다.
     *
     * @throws FeedException 주소가 부적절하거나, 응답이 실패거나, 너무 큰 경우
     */
    public String fetch(String rawUrl) {
        URI uri = normalize(rawUrl);

        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            requirePublic(uri);
            HttpResponse<InputStream> response = send(uri);
            int status = response.statusCode();

            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("location").orElse(null);
                if (location == null) {
                    throw new FeedException("리다이렉트에 대상 주소가 없습니다");
                }
                uri = uri.resolve(location);
                continue;
            }
            if (status != 200) {
                throw new FeedException("피드를 읽지 못했습니다 (HTTP " + status + ")");
            }
            return read(response);
        }
        throw new FeedException("리다이렉트가 너무 많습니다");
    }

    // ---------------------------------------------------------------- 내부

    private HttpResponse<InputStream> send(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(READ_TIMEOUT)
                .header("Accept", "text/calendar, text/plain;q=0.9, */*;q=0.5")
                .header("User-Agent", "LeoShift/1.0 (+calendar subscription)")
                .GET()
                .build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new FeedException("피드 서버에 연결하지 못했습니다: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FeedException("피드를 가져오다 중단됐습니다");
        }
    }

    /** 크기 한도까지만 읽는다. Content-Length를 믿지 않는다 — 안 줄 수도, 거짓일 수도 있다. */
    private String read(HttpResponse<InputStream> response) {
        try (InputStream in = response.body()) {
            byte[] body = in.readNBytes(maxBytes + 1);
            if (body.length > maxBytes) {
                throw new FeedException("피드가 너무 큽니다 (" + (maxBytes / 1024 / 1024) + "MB 제한)");
            }
            return new String(body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new FeedException("피드를 읽는 중 끊겼습니다: " + e.getMessage());
        }
    }

    /** {@code webcal://}은 구글·애플이 구독 주소로 나눠주는 형태다. https와 같다. */
    private static URI normalize(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new FeedException("주소가 비어 있습니다");
        }
        String text = rawUrl.trim();
        if (text.regionMatches(true, 0, "webcal://", 0, 9)) {
            text = "https://" + text.substring(9);
        }
        URI uri;
        try {
            uri = new URI(text);
        } catch (URISyntaxException e) {
            throw new FeedException("주소 형식이 아닙니다");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new FeedException("http 또는 https 주소만 구독할 수 있습니다");
        }
        if (uri.getHost() == null) {
            throw new FeedException("주소에 호스트가 없습니다");
        }
        // user:pass@host 형태로 파서를 헷갈리게 하는 우회를 막는다
        if (uri.getUserInfo() != null) {
            throw new FeedException("주소에 계정 정보를 넣을 수 없습니다");
        }
        return uri;
    }

    /**
     * 이 주소가 바깥을 가리키는지 확인한다.
     *
     * <p>이름이 여러 주소로 풀릴 수 있으므로 <b>전부</b> 본다. 하나라도 내부면 막는다.
     * 하나만 보고 통과시키면 공격자가 공개 주소와 내부 주소를 함께 등록해 통과시킨다.
     */
    private void requirePublic(URI uri) {
        if (allowPrivate) {
            return;
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException e) {
            throw new FeedException("주소를 찾을 수 없습니다: " + uri.getHost());
        }
        for (InetAddress address : addresses) {
            if (isPrivate(address)) {
                log.warn("내부망을 가리키는 피드 주소를 막았다: host={} address={}",
                        uri.getHost(), address.getHostAddress());
                throw new FeedException("내부망 주소는 구독할 수 없습니다");
            }
        }
    }

    private static boolean isPrivate(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            // 100.64.0.0/10 (CGNAT). 공유기와 통신사 장비가 여기 있다
            if (first == 100 && second >= 64 && second <= 127) {
                return true;
            }
            // 0.0.0.0/8, 169.254/16(위에서 걸리지만 명시), 192.0.0.0/24
            return first == 0 || (first == 192 && second == 0 && (bytes[2] & 0xFF) == 0);
        }
        // fc00::/7 — IPv6 유니크 로컬. isSiteLocalAddress()는 폐기된 fec0::/10만 본다
        return (bytes[0] & 0xFE) == 0xFC;
    }

    /** 사용자에게 그대로 보여줄 수 있는 문구여야 한다. 원격 서버 문구는 붙이지 않는다. */
    public static class FeedException extends RuntimeException {
        public FeedException(String message) {
            super(message);
        }
    }
}
