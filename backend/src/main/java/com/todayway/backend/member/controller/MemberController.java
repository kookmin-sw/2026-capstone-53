package com.todayway.backend.member.controller;

import com.todayway.backend.common.response.ApiResponse;
import com.todayway.backend.common.web.CurrentMember;
import com.todayway.backend.member.domain.Member;
import com.todayway.backend.member.dto.MemberResponse;
import com.todayway.backend.member.dto.MemberUpdateRequest;
import com.todayway.backend.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> getMe(@CurrentMember Member member) {
        return ResponseEntity.ok(ApiResponse.of(memberService.getMe(member)));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> update(
            @CurrentMember Member member,
            @RequestBody @Valid MemberUpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.of(memberService.update(member, req)));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> delete(@CurrentMember Member member) {
        memberService.softDelete(member);
        return ResponseEntity.noContent().build();
    }
}
