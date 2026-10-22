package com.app.sumpyo_be.domain.complaint.repository;

import com.app.sumpyo_be.domain.complaint.entity.Complaint;
import com.app.sumpyo_be.domain.complaint.entity.ComplaintStatus;
import com.app.sumpyo_be.global.common.TimeIdCursor;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** 민원 접수함 목록 조건. null 인 조건은 빠진다 */
public final class ComplaintSpecs {

  private ComplaintSpecs() {}

  public static Specification<Complaint> inBuilding(Long buildingId) {
    return (root, query, cb) -> buildingId == null ? null
        : cb.equal(root.get("room").get("building").get("id"), buildingId);
  }

  public static Specification<Complaint> inRoom(Long roomId) {
    return (root, query, cb) -> roomId == null ? null : cb.equal(root.get("room").get("id"), roomId);
  }

  /** 건물명·호실 번호·내용 부분 검색 (대소문자 무시, % _ 는 글자 그대로) */
  public static Specification<Complaint> search(String q) {
    return (root, query, cb) -> {
      if (q == null || q.isBlank()) {
        return null;
      }
      String pattern = "%" + q.strip().toLowerCase(Locale.ROOT)
          .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
      return cb.or(
          cb.like(cb.lower(root.get("room").get("building").get("name")), pattern, '\\'),
          cb.like(cb.lower(root.get("room").get("number")), pattern, '\\'),
          cb.like(cb.lower(root.get("content")), pattern, '\\'));
    };
  }

  public static Specification<Complaint> hasStatus(ComplaintStatus status) {
    return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
  }

  /** (created_at, id) < (cursor) : 같은 시각이어도 id 로 이어서 중복·누락이 없다 */
  public static Specification<Complaint> before(TimeIdCursor cursor) {
    return (root, query, cb) -> cursor == null ? null : cb.or(
        cb.lessThan(root.get("createdAt"), cursor.createdAt()),
        cb.and(cb.equal(root.get("createdAt"), cursor.createdAt()), cb.lessThan(root.get("id"), cursor.id())));
  }
}
