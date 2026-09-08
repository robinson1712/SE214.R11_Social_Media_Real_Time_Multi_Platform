package com.socialapp.dating.dto;

import com.socialapp.dating.entity.DatingProfile;

public record CandidateResponse(DatingProfile profile, int compatibilityScore) {
}
