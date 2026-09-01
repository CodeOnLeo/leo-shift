package io.github.codeonleo.leoshift.web.dto;

import io.github.codeonleo.leoshift.domain.group.Group;
import io.github.codeonleo.leoshift.domain.group.GroupMember;
import io.github.codeonleo.leoshift.domain.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** 그룹 관리 API의 요청 · 응답. */
public final class GroupDtos {

    private GroupDtos() {
    }

    public record SaveGroupRequest(
            @NotBlank(message = "그룹 이름을 입력해 주세요")
            @Size(max = 100, message = "그룹 이름은 100자를 넘을 수 없습니다")
            String name,

            @NotNull(message = "그룹 종류를 골라 주세요")
            Group.Kind kind,

            @Size(max = 500, message = "설명은 500자를 넘을 수 없습니다")
            String description,

            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "색은 #RRGGBB 형식이어야 합니다")
            String color) {
    }

    public record AddMemberRequest(
            @NotBlank(message = "이메일을 입력해 주세요")
            @Email(message = "이메일 형식이 아닙니다")
            String email,

            /** 비우면 오늘부터. 지난 프로젝트를 옮겨 적을 때 과거 날짜를 넣는다. */
            LocalDate joinedOn) {
    }

    public record UpdateMemberRequest(
            @NotNull(message = "시작일을 입력해 주세요")
            LocalDate joinedOn,

            /** null이면 현재 소속 중. */
            LocalDate leftOn) {
    }

    public record GroupSummaryResponse(
            Long id, String name, String kind, String color, String description,
            long memberCount, boolean owner) {

        public static GroupSummaryResponse from(Group group, long memberCount, boolean owner) {
            return new GroupSummaryResponse(
                    group.getId(), group.getName(), group.getKind().name(),
                    group.getColor(), group.getDescription(), memberCount, owner);
        }
    }

    /**
     * @param shared 이 사람이 자기 캘린더를 이 그룹에 공유했는가.
     *               <b>그룹에 넣는 것과 일정이 보이는 것은 별개다.</b> 공유는 각자가
     *               정하므로, 그러지 않으면 "왜 저 사람 줄이 비어 있지?"를 알 수 없다.
     */
    public record MemberResponse(
            Long memberId, Long userId, String name, String nickname, String email,
            String colorTag, String role,
            LocalDate joinedOn, LocalDate leftOn,
            boolean active, boolean self, boolean shared) {

        public static MemberResponse from(GroupMember member, Long viewerId, boolean shared) {
            User user = member.getUser();
            return new MemberResponse(
                    member.getId(), user.getId(), user.getName(), user.getNickname(), user.getEmail(),
                    user.getColorTag(), member.getRole().name(),
                    member.getJoinedOn(), member.getLeftOn(),
                    member.getLeftOn() == null,
                    user.getId().equals(viewerId),
                    shared);
        }
    }

    public record GroupDetailResponse(
            Long id, String name, String kind, String color, String description,
            boolean owner, List<MemberResponse> members) {
    }
}
