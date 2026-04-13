package io.github.codeonleo.leoshift.dto;

import java.util.List;

public record ShareGroupResponse(
        Long id,
        Long ownerUserId,
        String ownerName,
        String name,
        List<ShareGroupMemberResponse> members
) {
}
