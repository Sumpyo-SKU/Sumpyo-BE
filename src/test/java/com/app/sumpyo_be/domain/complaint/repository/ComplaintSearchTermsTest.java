package com.app.sumpyo_be.domain.complaint.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.sumpyo_be.domain.complaint.entity.ComplaintCategory;
import com.app.sumpyo_be.domain.complaint.repository.ComplaintSearchTerms.Term;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ComplaintSearchTermsTest {

  private static final List<String> BUILDINGS = List.of("북악관", "혜인관");

  private static List<String> texts(String q) {
    return ComplaintSearchTerms.parse(q, BUILDINGS).stream().map(Term::text).toList();
  }

  @Test
  void 호와_건물명을_떼고_번호만() {
    for (String q : List.of("520", "520호", "520 호", "북악관 520호", "북악관520호", "  북악관 520  ")) {
      assertThat(texts(q)).as(q).containsExactly("520");
    }
  }

  @Test
  void 숫자가_없으면_건물명은_그대로_검색어() {
    assertThat(texts("북악관")).containsExactly("북악관");
    assertThat(texts("혜인")).containsExactly("혜인");
  }

  @Test
  void 띄어쓰기로_여러_단어() {
    assertThat(texts("617 답답")).containsExactly("617", "답답");
    assertThat(texts("  ")).isEmpty();
    assertThat(texts(null)).isEmpty();
  }

  @Test
  void 민원_종류_한글_앞부분이나_어간으로_코드() {
    assertThat(ComplaintSearchTerms.categories("답답해요")).containsExactly(ComplaintCategory.STUFFY);
    assertThat(ComplaintSearchTerms.categories("답답")).containsExactly(ComplaintCategory.STUFFY);
    assertThat(ComplaintSearchTerms.categories("덥")).containsExactly(ComplaintCategory.HOT);
    assertThat(ComplaintSearchTerms.categories("덥다")).containsExactly(ComplaintCategory.HOT);
    assertThat(ComplaintSearchTerms.categories("더워요")).containsExactly(ComplaintCategory.HOT);
    assertThat(ComplaintSearchTerms.categories("춥")).containsExactly(ComplaintCategory.COLD);
    assertThat(ComplaintSearchTerms.categories("추워요")).containsExactly(ComplaintCategory.COLD);
    assertThat(ComplaintSearchTerms.categories("습해요")).containsExactly(ComplaintCategory.HUMID);
    assertThat(ComplaintSearchTerms.categories("냄새")).containsExactly(ComplaintCategory.SMELL);
    assertThat(ComplaintSearchTerms.categories("고장")).containsExactly(ComplaintCategory.BROKEN);
    assertThat(ComplaintSearchTerms.categories("설비")).containsExactly(ComplaintCategory.BROKEN);
    assertThat(ComplaintSearchTerms.categories("smell")).containsExactly(ComplaintCategory.SMELL);
  }

  @Test
  void 종류와_상관없는_단어는_빈_집합() {
    assertThat(ComplaintSearchTerms.categories("520")).isEmpty();
    assertThat(ComplaintSearchTerms.categories("졸려")).isEmpty();
    assertThat(ComplaintSearchTerms.parse("617 답답", BUILDINGS))
        .containsExactly(new Term("617", Set.of()), new Term("답답", Set.of(ComplaintCategory.STUFFY)));
  }
}
