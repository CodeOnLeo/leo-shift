package io.github.codeonleo.leoshift.schedule.preset;

import java.util.Objects;

/**
 * 기준일을 사용자에게 묻는 방법.
 *
 * <p>"패턴의 1일차가 언제인가요"는 대부분 답하지 못한다. 대신 아는 사실
 * ("가장 최근 야간 근무를 시작한 날")을 묻고 기준일을 역산한다.
 *
 * @param code     시퀀스에서 찾을 코드
 * @param question 사용자에게 보여줄 질문
 */
public record AnchorHint(String code, String question) {

    public AnchorHint {
        Objects.requireNonNull(code, "code는 필수다");
        Objects.requireNonNull(question, "question은 필수다");
    }
}
