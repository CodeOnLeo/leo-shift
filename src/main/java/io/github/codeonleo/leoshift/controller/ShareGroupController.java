package io.github.codeonleo.leoshift.controller;

import io.github.codeonleo.leoshift.dto.ShareGroupCreateRequest;
import io.github.codeonleo.leoshift.dto.ShareGroupMemberAddRequest;
import io.github.codeonleo.leoshift.dto.ShareGroupResponse;
import io.github.codeonleo.leoshift.dto.ShareGroupUpdateRequest;
import io.github.codeonleo.leoshift.service.ShareGroupService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/share-groups")
public class ShareGroupController {

    private final ShareGroupService shareGroupService;

    public ShareGroupController(ShareGroupService shareGroupService) {
        this.shareGroupService = shareGroupService;
    }

    @GetMapping
    public List<ShareGroupResponse> listMine() {
        return shareGroupService.listMine();
    }

    @PostMapping
    public ShareGroupResponse create(@Valid @RequestBody ShareGroupCreateRequest request) {
        return shareGroupService.create(request.name());
    }

    @PutMapping("/{groupId}")
    public ShareGroupResponse rename(@PathVariable Long groupId,
                                     @Valid @RequestBody ShareGroupUpdateRequest request) {
        return shareGroupService.rename(groupId, request.name());
    }

    @DeleteMapping("/{groupId}")
    public void delete(@PathVariable Long groupId) {
        shareGroupService.delete(groupId);
    }

    @PostMapping("/{groupId}/members")
    public ShareGroupResponse addMember(@PathVariable Long groupId,
                                        @Valid @RequestBody ShareGroupMemberAddRequest request) {
        return shareGroupService.addMember(groupId, request.email());
    }

    @DeleteMapping("/{groupId}/members/{memberUserId}")
    public ShareGroupResponse removeMember(@PathVariable Long groupId,
                                           @PathVariable Long memberUserId) {
        return shareGroupService.removeMember(groupId, memberUserId);
    }
}
