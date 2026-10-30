package com.app.sumpyo_be.domain.complaint.repository;

import com.app.sumpyo_be.domain.complaint.entity.ComplaintCategory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 접수함 검색어 정리. DB·Spring 에 의존하지 않는 순수 함수라 단위 테스트로 규칙을 고정한다.
 * - 앞뒤 공백 제거, 띄어쓰기로 나눈 단어는 모두 맞아야 한다 (AND)
 * - "520호"·"520 호"의 "호"는 뗀다. 숫자가 있으면 건물 이름 단어("북악관", "북악관520호"의 앞부분)도 뗀다
 *   → "520", "520호", "북악관 520호" 는 같은 검색
 * - 민원 종류 한글은 코드로: 단어가 이름의 앞부분이거나("답답" → 답답해요) 어간으로 시작하면("덥다" → 덥) 그 종류
 */
public final class ComplaintSearchTerms {

  private ComplaintSearchTerms() {}

  /**
   * 검색 단어 하나. 건물 이름·호실 번호·내용 중 하나에 text 가 들어 있거나, 종류가 categories 중 하나면 맞음
   * @param categories 한글(또는 코드)로 바꾼 민원 종류. 해당 없으면 빈 집합
   */
  public record Term(String text, Set<ComplaintCategory> categories) {}

  /** 종류별 검색 이름: 화면 이름 + 어간 (앞부분만 쳐도 맞도록) */
  static final Map<ComplaintCategory, List<String>> CATEGORY_WORDS = Map.of(
      ComplaintCategory.STUFFY, List.of("답답해요", "답답"),
      ComplaintCategory.HOT, List.of("더워요", "덥"),
      ComplaintCategory.COLD, List.of("추워요", "춥"),
      ComplaintCategory.HUMID, List.of("습해요", "습"),
      ComplaintCategory.SMELL, List.of("냄새나요", "냄새"),
      ComplaintCategory.BROKEN, List.of("설비 고장", "설비", "고장"));

  private static final String HO = "호";
  /** "520호", "520" */
  private static final Pattern ROOM_NUMBER = Pattern.compile("(\\d+)" + HO + "?");
  /** "북악관520호", "북악관520" */
  private static final Pattern BUILDING_AND_NUMBER = Pattern.compile("(\\D+?)(\\d+)" + HO + "?");

  /**
   * @param q             검색어 (null·공백이면 빈 목록 = 검색 안 함)
   * @param buildingNames 건물 이름들 (숫자와 같이 쓰면 떼어낸다)
   */
  public static List<Term> parse(String q, Collection<String> buildingNames) {
    if (q == null || q.isBlank()) {
      return List.of();
    }
    List<String> words = new ArrayList<>();
    boolean hasNumber = false;
    for (String word : q.strip().split("\\s+")) {
      if (word.equals(HO)) {
        continue;   // "520 호"
      }
      Matcher number = ROOM_NUMBER.matcher(word);
      Matcher attached = BUILDING_AND_NUMBER.matcher(word);
      if (number.matches()) {
        words.add(number.group(1));
        hasNumber = true;
      } else if (attached.matches() && isBuilding(attached.group(1), buildingNames)) {
        words.add(attached.group(2));   // "북악관520호" → 520
        hasNumber = true;
      } else {
        words.add(word);
      }
    }
    boolean dropBuildings = hasNumber;
    return words.stream()
        .filter(w -> !(dropBuildings && isBuilding(w, buildingNames)))
        .map(w -> new Term(w, categories(w)))
        .toList();
  }

  /** 단어가 가리키는 민원 종류. 코드(STUFFY)도 받는다 */
  static Set<ComplaintCategory> categories(String word) {
    Set<ComplaintCategory> found = EnumSet.noneOf(ComplaintCategory.class);
    String upper = word.toUpperCase(Locale.ROOT);
    for (ComplaintCategory c : ComplaintCategory.values()) {
      if (c.name().equals(upper)
          || CATEGORY_WORDS.get(c).stream().anyMatch(name -> name.startsWith(word) || word.startsWith(name))) {
        found.add(c);
      }
    }
    return found;
  }

  private static boolean isBuilding(String word, Collection<String> buildingNames) {
    return buildingNames.stream().anyMatch(name -> name.equalsIgnoreCase(word));
  }
}
