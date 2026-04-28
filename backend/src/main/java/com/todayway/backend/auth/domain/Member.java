package com.todayway.backend.auth.domain;

import com.todayway.backend.common.entity.BaseEntity;
import com.todayway.backend.common.ulid.UlidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.OffsetDateTime;

@Getter
@Entity
@Table(name = "member")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_uid", nullable = false, updatable = false, unique = true,
            columnDefinition = "CHAR(26)")
    private String memberUid;

    @Column(name = "login_id", nullable = false, unique = true, length = 50)
    private String loginId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    private Member(String loginId, String passwordHash, String nickname) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
    }

    public static Member create(String loginId, String passwordHash, String nickname) {
        return new Member(loginId, passwordHash, nickname);
    }

    @PrePersist
    void prePersist() {
        if (memberUid == null) {
            memberUid = UlidGenerator.generate();
        }
    }
}
