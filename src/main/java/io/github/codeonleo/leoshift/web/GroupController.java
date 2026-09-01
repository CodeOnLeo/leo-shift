package io.github.codeonleo.leoshift.web;

import io.github.codeonleo.leoshift.service.GroupService;
import io.github.codeonleo.leoshift.service.GroupTimelineService;
import io.github.codeonleo.leoshift.web.dto.GroupDtos.AddMemberRequest;
import io.github.codeonleo.leoshift.web.dto.GroupDtos.GroupDetailResponse;
import io.github.codeonleo.leoshift.web.dto.GroupDtos.GroupSummaryResponse;
import io.github.codeonleo.leoshift.web.dto.GroupDtos.MemberResponse;
import io.github.codeonleo.leoshift.web.dto.GroupDtos.SaveGroupRequest;
import io.github.codeonleo.leoshift.web.dto.GroupDtos.UpdateMemberRequest;
import io.github.codeonleo.leoshift.web.dto.TimelineDtos.TimelineResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;
    private final GroupTimelineService timelineService;

    public GroupController(GroupService groupService, GroupTimelineService timelineService) {
        this.groupService = groupService;
        this.timelineService = timelineService;
    }

    @GetMapping
    public List<GroupSummaryResponse> list() {
        return groupService.list();
    }

    @GetMapping("/{groupId}")
    public GroupDetailResponse detail(@PathVariable Long groupId) {
        return groupService.detail(groupId);
    }

    @PostMapping
    public GroupSummaryResponse create(@Valid @RequestBody SaveGroupRequest request) {
        return groupService.create(request);
    }

    @PutMapping("/{groupId}")
    public GroupSummaryResponse update(@PathVariable Long groupId,
                                       @Valid @RequestBody SaveGroupRequest request) {
        return groupService.update(groupId, request);
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> delete(@PathVariable Long groupId) {
        groupService.delete(groupId);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------- 멤버십

    @PostMapping("/{groupId}/members")
    public MemberResponse addMember(@PathVariable Long groupId,
                                    @Valid @RequestBody AddMemberRequest request) {
        return groupService.addMember(groupId, request);
    }

    /** 소속 기간 수정. 지난 프로젝트를 옮겨 적거나 날짜를 잘못 넣었을 때. */
    @PutMapping("/{groupId}/members/{memberId}")
    public MemberResponse updateMember(@PathVariable Long groupId,
                                       @PathVariable Long memberId,
                                       @Valid @RequestBody UpdateMemberRequest request) {
        return groupService.updateMember(groupId, memberId, request);
    }

    /**
     * 멤버를 내보낸다. <b>행을 지우는 게 아니라 종료일을 적는다.</b>
     *
     * @param leftOn 비우면 오늘까지
     */
    @DeleteMapping("/{groupId}/members/{memberId}")
    public ResponseEntity<Void> endMembership(
            @PathVariable Long groupId,
            @PathVariable Long memberId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate leftOn) {
        groupService.endMembership(groupId, memberId, leftOn);
        return ResponseEntity.noContent().build();
    }

    /** 내가 나간다. 소유자가 아니면 누구나 스스로 할 수 있다. */
    @PostMapping("/{groupId}/leave")
    public ResponseEntity<Void> leave(@PathVariable Long groupId) {
        groupService.leave(groupId);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------ 타임라인

    @GetMapping("/{groupId}/timeline")
    public TimelineResponse timeline(
            @PathVariable Long groupId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return timelineService.timeline(groupId, from, to);
    }
}
