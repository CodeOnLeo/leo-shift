package io.github.codeonleo.leoshift.schedule.preset;

/**
 * 교대조 하나.
 *
 * <p>사용자는 "몇 조인가요?"만 고르면 된다. {@code offset}만큼 시퀀스를 회전한 것이
 * 그 조의 근무가 된다. 기준일은 조와 무관하게 같으므로, 같은 사업장 사람들끼리
 * 같은 기준일을 쓰면 서로의 근무가 자동으로 맞아떨어진다.
 */
public record TeamOption(String label, int offset) {

    public TeamOption {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("조 이름은 필수다");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset은 0 이상이어야 한다: " + label + " = " + offset);
        }
    }
}
