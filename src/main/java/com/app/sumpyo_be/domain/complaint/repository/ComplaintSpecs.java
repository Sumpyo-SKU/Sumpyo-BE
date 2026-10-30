package com.app.sumpyo_be.domain.complaint.repository;

import com.app.sumpyo_be.domain.complaint.entity.Complaint;
import com.app.sumpyo_be.domain.complaint.entity.ComplaintCategory;
import com.app.sumpyo_be.domain.complaint.entity.ComplaintStatus;
import com.app.sumpyo_be.global.common.TimeIdCursor;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
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

  /**
   * 검색 (ComplaintSearchTerms 로 정리한 단어들). 단어마다 건물명·호실 번호·내용 부분 일치(대소문자 무시, % _ 는 글자 그대로)
   * 또는 민원 종류 포함 중 하나라도 맞아야 하고, 단어끼리는 모두 맞아야 한다 (AND). 단어가 없으면 조건 없음
   */
  public static Specification<Complaint> search(List<ComplaintSearchTerms.Term> terms) {
    return (root, query, cb) -> {
      if (terms == null || terms.isEmpty()) {
        return null;
      }
      // categories 는 "STUFFY,HOT" → ",STUFFY,HOT," 에서 ",HOT," 포함 여부로 (다른 코드의 일부와 헷갈리지 않게)
      Expression<String> categories = cb.concat(cb.concat(",", root.get("categories")), ",");
      List<Predicate> all = new ArrayList<>();
      for (ComplaintSearchTerms.Term term : terms) {
        String pattern = "%" + term.text().toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        List<Predicate> any = new ArrayList<>(List.of(
            cb.like(cb.lower(root.get("room").get("building").get("name")), pattern, '\\'),
            cb.like(cb.lower(root.get("room").get("number")), pattern, '\\'),
            cb.like(cb.lower(root.get("content")), pattern, '\\')));
        for (ComplaintCategory c : term.categories()) {
          any.add(cb.like(categories, "%," + c.name() + ",%"));
        }
        all.add(cb.or(any.toArray(Predicate[]::new)));
      }
      return cb.and(all.toArray(Predicate[]::new));
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
