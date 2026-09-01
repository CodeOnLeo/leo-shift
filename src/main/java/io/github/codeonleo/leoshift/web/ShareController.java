package io.github.codeonleo.leoshift.web;

import io.github.codeonleo.leoshift.service.SharingService;
import io.github.codeonleo.leoshift.web.dto.ShareDtos.SetShareRequest;
import io.github.codeonleo.leoshift.web.dto.ShareDtos.ShareOverviewResponse;
import io.github.codeonleo.leoshift.web.dto.ShareDtos.ShareTargetResponse;
import io.github.codeonleo.leoshift.web.dto.ShareDtos.TargetType;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공유 관리.
 *
 * <p>경로가 캘린더가 아니라 {@code /api/shares}인 이유는, 사용자가 묻는 질문이
 * "이 캘린더를 누가 보나"가 아니라 <b>"누가 내 뭘 보나"</b>이기 때문이다.
 */
@RestController
@RequestMapping("/api/shares")
public class ShareController {

    private final SharingService sharingService;

    public ShareController(SharingService sharingService) {
        this.sharingService = sharingService;
    }

    @GetMapping
    public ShareOverviewResponse overview() {
        return sharingService.overview();
    }

    /** 대상 하나의 공개 단계를 정한다. 처음 공유하는 것과 단계를 바꾸는 것이 같은 동작이다. */
    @PutMapping
    public ShareTargetResponse setLevel(@Valid @RequestBody SetShareRequest request) {
        return sharingService.setLevel(request);
    }

    @DeleteMapping("/{targetType}/{targetId}")
    public ResponseEntity<Void> revoke(@PathVariable TargetType targetType,
                                       @PathVariable Long targetId) {
        sharingService.revoke(targetType, targetId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/incoming/{shareId}/accept")
    public ResponseEntity<Void> accept(@PathVariable Long shareId) {
        sharingService.respond(shareId, true);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/incoming/{shareId}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long shareId) {
        sharingService.respond(shareId, false);
        return ResponseEntity.noContent().build();
    }
}
