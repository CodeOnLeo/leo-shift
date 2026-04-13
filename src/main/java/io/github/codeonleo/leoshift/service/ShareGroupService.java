package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.dto.ShareGroupMemberResponse;
import io.github.codeonleo.leoshift.dto.ShareGroupResponse;
import io.github.codeonleo.leoshift.entity.ShareGroup;
import io.github.codeonleo.leoshift.entity.ShareGroupMember;
import io.github.codeonleo.leoshift.entity.User;
import io.github.codeonleo.leoshift.repository.ShareGroupMemberRepository;
import io.github.codeonleo.leoshift.repository.ShareGroupRepository;
import io.github.codeonleo.leoshift.repository.UserRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ShareGroupService {

    private final ShareGroupRepository shareGroupRepository;
    private final ShareGroupMemberRepository shareGroupMemberRepository;
    private final UserRepository userRepository;
    private final CalendarAccessService calendarAccessService;

    @Transactional
    public List<ShareGroupResponse> listMine() {
        User currentUser = calendarAccessService.getCurrentUser();
        return shareGroupRepository.findByOwnerOrderByNameAsc(currentUser).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ShareGroupResponse create(String name) {
        User currentUser = calendarAccessService.getCurrentUser();
        String normalizedName = normalizeName(name);
        shareGroupRepository.findByOwnerAndName(currentUser, normalizedName)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("share_group_name_exists");
                });

        ShareGroup group = ShareGroup.builder()
                .owner(currentUser)
                .name(normalizedName)
                .build();
        return toResponse(shareGroupRepository.save(group));
    }

    @Transactional
    public ShareGroupResponse rename(Long groupId, String name) {
        ShareGroup group = requireOwnedGroup(groupId);
        String normalizedName = normalizeName(name);
        shareGroupRepository.findByOwnerAndName(group.getOwner(), normalizedName)
                .filter(existing -> !existing.getId().equals(group.getId()))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("share_group_name_exists");
                });
        group.setName(normalizedName);
        return toResponse(shareGroupRepository.save(group));
    }

    @Transactional
    public void delete(Long groupId) {
        ShareGroup group = requireOwnedGroup(groupId);
        shareGroupMemberRepository.deleteByGroup(group);
        shareGroupRepository.delete(group);
    }

    @Transactional
    public ShareGroupResponse addMember(Long groupId, String email) {
        ShareGroup group = requireOwnedGroup(groupId);
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("email_required");
        }

        User targetUser = userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new IllegalArgumentException("user_not_found"));

        if (group.getOwner().getId().equals(targetUser.getId())) {
            throw new IllegalArgumentException("share_group_owner_cannot_be_member");
        }

        shareGroupMemberRepository.findByGroupAndUser(group, targetUser)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("share_group_member_exists");
                });

        ShareGroupMember member = ShareGroupMember.builder()
                .group(group)
                .user(targetUser)
                .build();
        shareGroupMemberRepository.save(member);
        return toResponse(group);
    }

    @Transactional
    public ShareGroupResponse removeMember(Long groupId, Long memberUserId) {
        ShareGroup group = requireOwnedGroup(groupId);
        User targetUser = userRepository.findById(memberUserId)
                .orElseThrow(() -> new IllegalArgumentException("user_not_found"));
        ShareGroupMember member = shareGroupMemberRepository.findByGroupAndUser(group, targetUser)
                .orElseThrow(() -> new IllegalArgumentException("share_group_member_not_found"));
        shareGroupMemberRepository.delete(member);
        return toResponse(group);
    }

    private ShareGroup requireOwnedGroup(Long groupId) {
        User currentUser = calendarAccessService.getCurrentUser();
        ShareGroup group = shareGroupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("share_group_not_found"));
        if (group.getOwner() == null || !group.getOwner().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("share_group_owner_only");
        }
        return group;
    }

    private ShareGroupResponse toResponse(ShareGroup group) {
        List<ShareGroupMemberResponse> members = shareGroupMemberRepository.findByGroup(group).stream()
                .map(this::toMemberResponse)
                .toList();
        return new ShareGroupResponse(
                group.getId(),
                group.getOwner().getId(),
                group.getOwner().getName(),
                group.getName(),
                members
        );
    }

    private ShareGroupMemberResponse toMemberResponse(ShareGroupMember member) {
        return new ShareGroupMemberResponse(
                member.getId(),
                member.getUser().getId(),
                member.getUser().getName(),
                member.getUser().getEmail()
        );
    }

    private String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("share_group_name_required");
        }
        String normalized = name.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("share_group_name_required");
        }
        return normalized;
    }
}
