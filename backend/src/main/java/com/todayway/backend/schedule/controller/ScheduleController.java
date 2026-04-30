package com.todayway.backend.schedule.controller;

import com.todayway.backend.common.pagination.CursorRequest;
import com.todayway.backend.common.pagination.CursorResponse;
import com.todayway.backend.common.response.ApiResponse;
import com.todayway.backend.common.web.CurrentMember;
import com.todayway.backend.schedule.dto.CreateScheduleRequest;
import com.todayway.backend.schedule.dto.ScheduleListItem;
import com.todayway.backend.schedule.dto.ScheduleResponse;
import com.todayway.backend.schedule.dto.UpdateScheduleRequest;
import com.todayway.backend.schedule.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private static final String SCHEDULE_PREFIX = "sch_";

    private final ScheduleService scheduleService;

    @PostMapping
    public ResponseEntity<ApiResponse<ScheduleResponse>> create(
            @CurrentMember String memberUid,
            @RequestBody @Valid CreateScheduleRequest req) {
        ScheduleResponse resp = scheduleService.create(memberUid, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(resp));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CursorResponse<ScheduleListItem>>> list(
            @CurrentMember String memberUid,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor) {
        CursorResponse<ScheduleListItem> resp = scheduleService.list(
                memberUid, from, to, new CursorRequest(limit, cursor));
        return ResponseEntity.ok(ApiResponse.of(resp));
    }

    @GetMapping("/{scheduleId}")
    public ResponseEntity<ApiResponse<ScheduleResponse>> get(
            @CurrentMember String memberUid,
            @PathVariable String scheduleId) {
        ScheduleResponse resp = scheduleService.get(memberUid, stripPrefix(scheduleId));
        return ResponseEntity.ok(ApiResponse.of(resp));
    }

    @PatchMapping("/{scheduleId}")
    public ResponseEntity<ApiResponse<ScheduleResponse>> update(
            @CurrentMember String memberUid,
            @PathVariable String scheduleId,
            @RequestBody @Valid UpdateScheduleRequest req) {
        ScheduleResponse resp = scheduleService.update(memberUid, stripPrefix(scheduleId), req);
        return ResponseEntity.ok(ApiResponse.of(resp));
    }

    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> delete(
            @CurrentMember String memberUid,
            @PathVariable String scheduleId) {
        scheduleService.delete(memberUid, stripPrefix(scheduleId));
        return ResponseEntity.noContent().build();
    }

    /**
     * 클라이언트는 명세 §1.7 응답 형식("sch_{ULID}")으로 요청 → DB의 raw scheduleUid로 변환.
     */
    private static String stripPrefix(String scheduleId) {
        return scheduleId.startsWith(SCHEDULE_PREFIX) ? scheduleId.substring(SCHEDULE_PREFIX.length()) : scheduleId;
    }
}
