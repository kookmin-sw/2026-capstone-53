package com.todayway.backend.member.domain;

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

/**
 * 회원 도메인 — 명세 §3 / V1__init.sql {@code member} 테이블 정합.
 *
 * <p>v1.1.22 (이슈 #31) — soft delete → hard delete 전환. {@code @SQLRestriction} /
 * {@code deleted_at} 컬럼 / {@code softDelete()} 제거. 회원 탈퇴는 {@code memberRepository.delete()}
 * 로 row 자체 삭제 + FK ON DELETE CASCADE 가 refresh_token / schedule / push_subscription 일괄 정리.
 */
@Getter
@Entity
@Table(name = "member")
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

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updatePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
