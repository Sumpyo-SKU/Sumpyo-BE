package com.app.sumpyo_be.domain.complaint.service;

import com.app.sumpyo_be.domain.complaint.dto.response.ComplaintQrInfoResponse;
import com.app.sumpyo_be.domain.member.entity.Role;
import com.app.sumpyo_be.domain.member.service.MemberAccess;
import com.app.sumpyo_be.domain.room.entity.Room;
import com.app.sumpyo_be.domain.room.entity.RoomType;
import com.app.sumpyo_be.domain.room.repository.RoomRepository;
import com.app.sumpyo_be.global.exception.CustomException;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 강의실 민원 QR 발급 (권한 W, A). QR 내용 = {FRONT_BASE_URL}/complaint?code={room.code} */
@Service
@Transactional(readOnly = true)
public class ComplaintQrService {

  public static final int SIZE_PX = 512;

  private final MemberAccess memberAccess;
  private final RoomRepository roomRepository;
  private final String frontBaseUrl;

  public ComplaintQrService(MemberAccess memberAccess, RoomRepository roomRepository,
      @Value("${sumpyo.complaint.front-base-url}") String frontBaseUrl) {
    this.memberAccess = memberAccess;
    this.roomRepository = roomRepository;
    this.frontBaseUrl = frontBaseUrl.strip().replaceAll("/+$", "");
  }

  public ComplaintQrInfoResponse info(Long roomId) {
    Room room = lectureRoom(roomId);
    return new ComplaintQrInfoResponse(room.getId(), room.getBuilding().getName() + " " + room.getNumber() + "호",
        url(room));
  }

  /** @return 파일 이름에 쓸 room code 와 PNG */
  public QrImage image(Long roomId) {
    Room room = lectureRoom(roomId);
    return new QrImage(room.getCode(), png(url(room)));
  }

  public record QrImage(String code, byte[] png) {}

  private Room lectureRoom(Long roomId) {
    memberAccess.require(Role.WORKER, Role.ADMIN);
    Room room = roomRepository.findWithBuildingById(roomId)
        .orElseThrow(() -> new CustomException(GlobalErrorCode.ROOM_NOT_FOUND));
    if (room.getType() != RoomType.LECTURE) {
      throw new CustomException(GlobalErrorCode.QR_NOT_LECTURE);
    }
    return room;
  }

  String url(Room room) {
    return frontBaseUrl + "/complaint?code=" + URLEncoder.encode(room.getCode(), StandardCharsets.UTF_8);
  }

  /** 오류 정정 M, 여백 2칸 */
  static byte[] png(String content) {
    try {
      BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, SIZE_PX, SIZE_PX,
          Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2,
              EncodeHintType.CHARACTER_SET, "UTF-8"));
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      MatrixToImageWriter.writeToStream(matrix, "PNG", out);
      return out.toByteArray();
    } catch (WriterException e) {
      throw new IllegalStateException("QR 생성 실패", e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
