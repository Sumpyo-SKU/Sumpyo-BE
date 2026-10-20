package com.app.sumpyo_be.global.room.service;

import com.app.sumpyo_be.global.exception.CustomException;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import com.app.sumpyo_be.global.room.dto.RoomLookupResponse;
import com.app.sumpyo_be.global.room.entity.Room;
import com.app.sumpyo_be.global.room.repository.RoomRepository;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 학생 민원 화면의 "위치 확인". QR 링크는 code, 위치 선택 시트는 buildingId + number 로 찾는다 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomLookupService {

  /** "618호", "618 호", "618호실" 의 뒤쪽 '호(실)' */
  private static final Pattern ROOM_SUFFIX = Pattern.compile("\\s*호실?$");

  private final RoomRepository roomRepository;

  /**
   * code 가 있으면 code 로, 없으면 buildingId + number 로 찾는다. 둘 다 없으면 400.
   * 강의실 데이터가 없는 건물(북악관 외)은 404 가 정상이다.
   */
  public RoomLookupResponse lookup(String code, Long buildingId, String number) {
    Optional<Room> room;
    if (code != null && !code.isBlank()) {
      room = roomRepository.findWithBuildingByCode(code.trim());
    } else {
      String normalized = normalizeNumber(number);
      if (buildingId == null || normalized.isEmpty()) {
        throw new CustomException(GlobalErrorCode.INVALID_ROOM_LOOKUP);
      }
      room = roomRepository.findWithBuildingByBuildingIdAndNumber(buildingId, normalized);
    }
    return room.map(RoomLookupResponse::from)
        .orElseThrow(() -> new CustomException(GlobalErrorCode.ROOM_LOOKUP_NOT_FOUND));
  }

  /** 앞뒤 공백과 뒤에 붙은 '호'를 뗀다. null 이면 빈 문자열 */
  static String normalizeNumber(String number) {
    if (number == null) {
      return "";
    }
    return ROOM_SUFFIX.matcher(number.trim()).replaceFirst("").trim();
  }
}
