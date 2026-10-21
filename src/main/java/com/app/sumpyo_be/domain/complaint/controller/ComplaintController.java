package com.app.sumpyo_be.domain.complaint.controller;

import com.app.sumpyo_be.global.common.BaseResponse;
import com.app.sumpyo_be.domain.complaint.dto.ComplaintCreateRequest;
import com.app.sumpyo_be.domain.complaint.dto.ComplaintCreateResponse;
import com.app.sumpyo_be.domain.complaint.service.ComplaintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 학생 민원(QR). 로그인 없는 공개 API */
@RestController
@Tag(name = "Complaint", description = "학생 민원 (QR, 로그인 없음)")
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
public class ComplaintController {

  private final ComplaintService complaintService;

  @Operation(summary = "민원 제출 (공개)",
      description = "학생 민원을 접수합니다(status RECEIVED). 접수 시점 최근 10분 내 CO2·온도·습도를 함께 저장합니다. "
          + "categories 1개 이상(중복 제거), content 최대 500자, clientId 필수. "
          + "같은 clientId 로 같은 방에 10분 안에 다시 내면 429 \"잠시 후 다시 알려주세요\". 없는 roomId 는 404.")
  @PostMapping
  public BaseResponse<ComplaintCreateResponse> create(@Valid @RequestBody ComplaintCreateRequest request) {
    return BaseResponse.success("민원이 접수되었어요.", complaintService.create(request));
  }
}
