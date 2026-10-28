package com.app.sumpyo_be.domain.talk.service;

import com.app.sumpyo_be.domain.building.entity.Building;
import com.app.sumpyo_be.global.exception.CustomException;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import com.app.sumpyo_be.domain.member.entity.Member;
import com.app.sumpyo_be.domain.member.entity.MemberZone;
import com.app.sumpyo_be.domain.member.repository.MemberRepository;
import com.app.sumpyo_be.domain.member.repository.MemberZoneRepository;
import com.app.sumpyo_be.domain.member.entity.Role;
import com.app.sumpyo_be.domain.member.service.MemberAccess;
import com.app.sumpyo_be.domain.room.entity.Room;
import com.app.sumpyo_be.domain.talk.dto.request.TalkCreateRequest;
import com.app.sumpyo_be.domain.talk.dto.response.TalkCreateResponse;
import com.app.sumpyo_be.domain.talk.dto.response.TalkDeleteResponse;
import com.app.sumpyo_be.domain.talk.dto.response.TalkDetailResponse;
import com.app.sumpyo_be.domain.talk.dto.response.TalkReplyDeleteResponse;
import com.app.sumpyo_be.domain.talk.dto.response.TalkListResponse;
import com.app.sumpyo_be.domain.talk.dto.request.TalkReplyRequest;
import com.app.sumpyo_be.domain.talk.dto.response.TalkReplyResponse;
import com.app.sumpyo_be.domain.talk.entity.TalkPost;
import com.app.sumpyo_be.domain.talk.entity.TalkReply;
import com.app.sumpyo_be.domain.talk.entity.TalkStatus;
import com.app.sumpyo_be.domain.talk.repository.TalkPostRepository;
import com.app.sumpyo_be.domain.talk.repository.TalkPostSpecs;
import com.app.sumpyo_be.domain.talk.repository.TalkReplyRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 캠퍼스톡 (권한 W). 소통 범위 = 내 건물, "연락 볼 구역" 은 목록 필터일 뿐 */
@Service
@RequiredArgsConstructor
public class TalkService {

  static final int MAX_CONTENT_LENGTH = 300;
  static final int PREVIEW_LENGTH = 50;
  static final String DELETED_REPLY = "삭제된 답변입니다";

  private final MemberAccess memberAccess;
  private final MemberRepository memberRepository;
  private final MemberZoneRepository zoneRepository;
  private final TalkAccess talkAccess;
  private final TalkFilterService filterService;
  private final TalkPostRepository postRepository;
  private final TalkReplyRepository replyRepository;
  private final TalkSuggestionService suggestionService;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  /**
   * 내 건물 글 중 연락 볼 구역 필터에 맞는 것, 최신순(ID 내림차순 = 보낸 순서의 역순), 커서 기반 무한 스크롤.
   * offset 페이징과 달리 스크롤 중 새 글이 와도 중복·누락이 없다. counts 는 같은 필터 기준 전체 개수.
   * @param cursor 이전 응답의 nextCursor (그 글보다 오래된 글부터). null 이면 처음부터
   */
  @Transactional(readOnly = true)
  public TalkListResponse list(TalkStatus status, Long cursor, int size) {
    Member me = memberAccess.require(Role.WORKER);
    Building building = talkAccess.myBuilding(me);
    Map<Integer, List<Room>> filter = filterService.effectiveFilter(me, building);

    Map<Integer, Set<Long>> roomIdsByFloor = new LinkedHashMap<>();
    filter.forEach((floor, rooms) ->
        roomIdsByFloor.put(floor, rooms.stream().map(Room::getId).collect(Collectors.toSet())));
    Specification<TalkPost> scope = TalkPostSpecs.inScope(building.getId(), roomIdsByFloor)
        .and(TalkPostSpecs.notDeleted());

    // size + 1 개를 읽어 다음이 있는지 판단 (첫 페이지라 Page 의 count 쿼리는 꽉 찼을 때만 나간다)
    List<TalkPost> fetched = postRepository.findAll(
        scope.and(TalkPostSpecs.hasStatus(status)).and(TalkPostSpecs.before(cursor)),
        PageRequest.of(0, size + 1, Sort.by(Sort.Order.desc("id")))).getContent();
    boolean hasNext = fetched.size() > size;
    List<TalkPost> posts = hasNext ? fetched.subList(0, size) : fetched;
    long unread = postRepository.count(scope.and(TalkPostSpecs.hasStatus(TalkStatus.UNREAD)));
    long checking = postRepository.count(scope.and(TalkPostSpecs.hasStatus(TalkStatus.CHECKING)));
    long answered = postRepository.count(scope.and(TalkPostSpecs.hasStatus(TalkStatus.ANSWERED)));

    Map<Long, String> senderNames = memberRepository.findAllById(
            posts.stream().map(TalkPost::getSenderId).distinct().toList()).stream()
        .collect(Collectors.toMap(Member::getId, Member::getName));
    return TalkListResponse.builder()
        .building(building.getName())
        .filter(TalkFilterService.toFloors(filter))
        .counts(new TalkListResponse.Counts(unread + checking + answered, unread, checking, answered))
        .content(posts.stream().map(p -> TalkListResponse.Item.builder()
            .talkId(p.getId())
            .target(p.target())
            .preview(preview(p.getContent()))
            .status(p.getStatus())
            .sentByMe(p.isSentBy(me.getId()))
            .senderName(senderNames.get(p.getSenderId()))
            .createdAt(p.getCreatedAt())
            .build()).toList())
        .hasNext(hasNext)
        .nextCursor(hasNext ? posts.getLast().getId() : null)
        .build();
  }

  /** 건물은 내 건물로 자동. floor 1~maxFloor, roomId 는 내 건물·그 층의 방 */
  @Transactional
  public TalkCreateResponse create(TalkCreateRequest req) {
    Member me = memberAccess.require(Role.WORKER);
    Building building = talkAccess.myBuilding(me);
    talkAccess.requireFloor(building, req.getFloor());
    Room room = req.getRoomId() == null ? null
        : talkAccess.requireRoomOnFloor(building, req.getFloor(), req.getRoomId());
    TalkPost post = TalkPost.create(me.getId(), building, req.getFloor(), room, content(req.getContent()),
        LocalDateTime.now(clock));
    postRepository.save(post);
    // 추천 답변은 커밋 후 비동기로 만든다 (LLM 을 기다리지 않고 바로 응답)
    eventPublisher.publishEvent(new TalkPostCreatedEvent(post.getId()));
    return new TalkCreateResponse(post.getId());
  }

  /**
   * 상세. 보낸 사람이 아닌 근로생이 열면 UNREAD → CHECKING. 추천 답변 규칙은 TalkSuggestionService.forDetail
   * (LLM 호출 없음). 상태 변경은 조건부 UPDATE 라 동시에 열어도 한 번만 바뀐다.
   */
  public TalkDetailResponse detail(Long talkId) {
    Member me = memberAccess.require(Role.WORKER);
    TalkPost post = findInMyBuilding(talkId, me);
    boolean sentByMe = post.isSentBy(me.getId());
    if (!sentByMe) {
      if (post.getStatus() == TalkStatus.UNREAD) {
        postRepository.changeStatus(talkId, TalkStatus.UNREAD, TalkStatus.CHECKING);
      }
      post = findPost(talkId);
    }
    List<String> suggestions = sentByMe ? List.of() : suggestionService.forDetail(post);

    // 삭제한 답변은 자리를 남겨 대화 순서를 유지한다
    List<TalkDetailResponse.Reply> replies = replyRepository.findWithMemberByPostId(talkId).stream()
        .map(r -> new TalkDetailResponse.Reply(r.getId(), r.isDeleted() ? DELETED_REPLY : r.getContent(),
            r.getMember().getName(), r.isSuggested(), r.isWrittenBy(me.getId()), r.isDeleted(), r.getCreatedAt()))
        .toList();
    return TalkDetailResponse.builder()
        .talkId(post.getId())
        .building(post.getBuilding().getName())
        .target(post.target())
        .content(post.getContent())
        .status(post.getStatus())
        .sentByMe(sentByMe)
        .createdAt(post.getCreatedAt())
        .suggestions(suggestions)
        .sender(sender(post.getSenderId()))
        .replies(replies)
        .build();
  }

  /**
   * 내 건물 글이면 누구나 (글쓴이 본인도). 다른 근로생의 답변이 달리면 ANSWERED,
   * 글쓴이 본인 답변은 상태를 바꾸지 않는다 (UNREAD·CHECKING 유지).
   */
  @Transactional
  public TalkReplyResponse reply(Long talkId, TalkReplyRequest req) {
    Member me = memberAccess.require(Role.WORKER);
    TalkPost post = findInMyBuilding(talkId, me);
    TalkReply reply = TalkReply.create(post, me, content(req.getContent()),
        Boolean.TRUE.equals(req.getIsSuggested()), LocalDateTime.now(clock));
    replyRepository.save(reply);
    if (!post.isSentBy(me.getId())) {
      post.answered();
    }
    return new TalkReplyResponse(reply.getId(), post.getStatus(), reply.getCreatedAt());
  }

  /** 보낸 사람 본인만 (아니면 403). 삭제하면 목록·상세에서 사라진다 (이미 삭제했으면 404) */
  @Transactional
  public TalkDeleteResponse delete(Long talkId) {
    Member me = memberAccess.require(Role.WORKER);
    TalkPost post = findInMyBuilding(talkId, me);
    if (!post.isSentBy(me.getId())) {
      throw new CustomException(GlobalErrorCode.TALK_DELETE_DENIED);
    }
    post.delete(LocalDateTime.now(clock));
    return new TalkDeleteResponse(talkId);
  }

  /**
   * 답변 쓴 사람 본인만 (아니면 403). 이미 삭제했으면 그대로 200.
   * 남은 "다른 근로생 답변" 이 없으면 글 상태 ANSWERED → CHECKING (글쓴이 답변만 남아도).
   */
  @Transactional
  public TalkReplyDeleteResponse deleteReply(Long talkId, Long replyId) {
    Member me = memberAccess.require(Role.WORKER);
    TalkPost post = findInMyBuilding(talkId, me);
    TalkReply reply = replyRepository.findInPost(replyId, talkId)
        .orElseThrow(() -> new CustomException(GlobalErrorCode.TALK_REPLY_NOT_FOUND));
    if (!reply.isWrittenBy(me.getId())) {
      throw new CustomException(GlobalErrorCode.TALK_REPLY_DELETE_DENIED);
    }
    reply.delete(LocalDateTime.now(clock));
    replyRepository.flush();
    if (!replyRepository.existsByPostIdAndDeletedAtIsNullAndMemberIdNot(talkId, post.getSenderId())) {
      post.noRepliesLeft();
    }
    return new TalkReplyDeleteResponse(replyId, post.getStatus());
  }

  /** 글쓴이 이름 + 담당 구역 ("북악관 5·6층", 여러 건물이면 첫 건물, 없으면 null) */
  private TalkDetailResponse.Sender sender(Long senderId) {
    String name = memberRepository.findById(senderId).map(Member::getName).orElse(null);
    List<MemberZone> zones = zoneRepository.findWithBuildingByMemberId(senderId);   // 건물 표시 순서 → 층
    if (zones.isEmpty()) {
      return new TalkDetailResponse.Sender(name, null);
    }
    Long firstBuilding = zones.getFirst().getBuilding().getId();
    String floors = zones.stream().filter(z -> z.getBuilding().getId().equals(firstBuilding))
        .map(z -> String.valueOf(z.getFloor())).collect(Collectors.joining("·"));
    return new TalkDetailResponse.Sender(name, zones.getFirst().getBuilding().getName() + " " + floors + "층");
  }

  private TalkPost findInMyBuilding(Long talkId, Member me) {
    TalkPost post = findPost(talkId);
    if (!post.getBuilding().getId().equals(talkAccess.myBuilding(me).getId())) {
      throw new CustomException(GlobalErrorCode.TALK_ACCESS_DENIED);
    }
    return post;
  }

  private TalkPost findPost(Long talkId) {
    return postRepository.findDetailById(talkId)
        .orElseThrow(() -> new CustomException(GlobalErrorCode.TALK_NOT_FOUND));
  }

  /** 앞뒤 공백 제거 후 1~300자 */
  static String content(String content) {
    String trimmed = content == null ? "" : content.strip();
    if (trimmed.isEmpty() || trimmed.length() > MAX_CONTENT_LENGTH) {
      throw new CustomException(GlobalErrorCode.INVALID_TALK_CONTENT);
    }
    return trimmed;
  }

  /** 카드 미리보기: 줄바꿈을 공백으로, 최대 50자 */
  static String preview(String content) {
    String oneLine = content.replaceAll("\\s+", " ").strip();
    return oneLine.length() <= PREVIEW_LENGTH ? oneLine : oneLine.substring(0, PREVIEW_LENGTH) + "…";
  }
}
