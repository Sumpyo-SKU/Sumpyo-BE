package com.app.sumpyo_be.domain.talk.repository;

import com.app.sumpyo_be.domain.talk.entity.TalkPost;
import com.app.sumpyo_be.domain.talk.entity.TalkStatus;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

/** 캠퍼스톡 목록 조건 */
public final class TalkPostSpecs {

  private TalkPostSpecs() {
  }

  /**
   * 내 건물 + 연락 볼 구역 필터. 층마다의 조건을 OR 로 묶는다.
   * @param roomIdsByFloor 비어 있으면 건물 전체.
   *                       값(강의실 ID)이 비면 그 층의 모든 글, 있으면 그 강의실로 보낸 글만 (층 전체 글 제외)
   */
  public static Specification<TalkPost> inScope(Long buildingId, Map<Integer, Set<Long>> roomIdsByFloor) {
    return (root, query, cb) -> {
      Predicate inBuilding = cb.equal(root.get("building").get("id"), buildingId);
      if (roomIdsByFloor.isEmpty()) {
        return inBuilding;
      }
      List<Predicate> perFloor = new ArrayList<>();
      roomIdsByFloor.forEach((floor, roomIds) -> {
        Predicate onFloor = cb.equal(root.get("floor"), floor);
        perFloor.add(roomIds.isEmpty() ? onFloor : cb.and(onFloor, root.get("room").get("id").in(roomIds)));
      });
      return cb.and(inBuilding, cb.or(perFloor.toArray(Predicate[]::new)));
    };
  }

  public static Specification<TalkPost> hasStatus(TalkStatus status) {
    return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
  }

  /** 무한 스크롤: cursor(이전 목록 마지막 글 ID)보다 오래된 글. null 이면 처음부터 */
  public static Specification<TalkPost> before(Long cursor) {
    return (root, query, cb) -> cursor == null ? cb.conjunction() : cb.lessThan(root.get("id"), cursor);
  }
}
