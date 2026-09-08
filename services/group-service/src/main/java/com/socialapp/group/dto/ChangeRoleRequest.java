package com.socialapp.group.dto;

import com.socialapp.group.entity.MemberRole;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull MemberRole role) {
}
