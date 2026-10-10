package com.socialapp.common.dto;

/** Minimal service-to-service result for checking access to a content target. */
public record ContentAccessResponse(boolean exists, boolean accessible, String ownerId) {
}
