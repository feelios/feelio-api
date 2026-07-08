package com.korit.feelioapi.domain.meta.service;

import com.korit.feelioapi.domain.meta.dto.MetaResponse;
import com.korit.feelioapi.domain.meta.entity.Category;
import com.korit.feelioapi.domain.meta.entity.Emotion;
import com.korit.feelioapi.domain.meta.mapper.MetaMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * MetaService 단위 테스트 (계약 §5). MetaMapper 목킹으로 매핑·필드 노출 검증.
 */
@ExtendWith(MockitoExtension.class)
class MetaServiceTest {

    @Mock private MetaMapper metaMapper;

    @InjectMocks private MetaService metaService;

    private Emotion emotion(long id, String name, String color, int sort) {
        Emotion e = new Emotion();
        e.setEmotionId(id);
        e.setName(name);
        e.setColor(color);
        e.setCharacterKey("stress");
        e.setActive(true);
        e.setSortOrder(sort);
        return e;
    }

    private Category category(long id, String name, String type, int sort) {
        Category c = new Category();
        c.setCategoryId(id);
        c.setName(name);
        c.setType(type);
        c.setActive(true);
        c.setSortOrder(sort);
        return c;
    }

    @Test
    void 활성_감정_카테고리를_계약필드로_매핑한다() {
        when(metaMapper.findActiveEmotions())
                .thenReturn(List.of(emotion(4L, "스트레스", "#A68BEA", 4)));
        when(metaMapper.findActiveCategories())
                .thenReturn(List.of(category(3L, "카페", "EXPENSE", 3)));

        MetaResponse response = metaService.getMeta();

        assertThat(response.emotions()).hasSize(1);
        assertThat(response.emotions().get(0).emotionId()).isEqualTo(4L);
        assertThat(response.emotions().get(0).name()).isEqualTo("스트레스");
        assertThat(response.emotions().get(0).color()).isEqualTo("#A68BEA");
        assertThat(response.emotions().get(0).sortOrder()).isEqualTo(4);

        assertThat(response.categories()).hasSize(1);
        assertThat(response.categories().get(0).categoryId()).isEqualTo(3L);
        assertThat(response.categories().get(0).name()).isEqualTo("카페");
        assertThat(response.categories().get(0).type()).isEqualTo("EXPENSE");
        assertThat(response.categories().get(0).sortOrder()).isEqualTo(3);
    }

    @Test
    void 매퍼가_반환한_순서를_그대로_유지한다() {
        when(metaMapper.findActiveEmotions()).thenReturn(List.of(
                emotion(1L, "신남", "#FF8A62", 1),
                emotion(2L, "설렘", "#F28AB7", 2)
        ));
        when(metaMapper.findActiveCategories()).thenReturn(List.of(
                category(1L, "식비", "EXPENSE", 1),
                category(9L, "급여", "INCOME", 1)
        ));

        MetaResponse response = metaService.getMeta();

        assertThat(response.emotions()).extracting("name")
                .containsExactly("신남", "설렘");
        assertThat(response.categories()).extracting("type")
                .containsExactly("EXPENSE", "INCOME");
    }

    @Test
    void 빈_마스터는_빈_배열로_반환한다() {
        when(metaMapper.findActiveEmotions()).thenReturn(List.of());
        when(metaMapper.findActiveCategories()).thenReturn(List.of());

        MetaResponse response = metaService.getMeta();

        assertThat(response.emotions()).isEmpty();
        assertThat(response.categories()).isEmpty();
    }
}
