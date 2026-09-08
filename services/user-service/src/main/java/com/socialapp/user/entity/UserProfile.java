package com.socialapp.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile {

    @Id
    private String id;

    @Column(nullable = false)
    private String fullName;

    private String avatarUrl;

    private String coverUrl;

    @Column(columnDefinition = "text")
    private String bio;

    private LocalDate dob;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private String location;

    /** Free-text "Làm việc tại X" / "Học tại Y" line, Facebook-style — a
     * single field rather than Facebook's real multi-entry work/education
     * history, which this app doesn't model. */
    private String workplace;

    /** Facebook-style reciprocal read receipts: off for me also hides the other person's "seen" status from me. */
    @Column(nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private boolean readReceiptsEnabled = true;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @jakarta.persistence.PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }
}
