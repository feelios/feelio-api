package com.korit.feelioapi.domain.universe.service;

import com.korit.feelioapi.domain.universe.dto.TopCategoryDto;
import com.korit.feelioapi.domain.universe.dto.GoalRow;
import com.korit.feelioapi.domain.universe.dto.MonthKey;
import com.korit.feelioapi.domain.universe.dto.ScenarioDto;
import com.korit.feelioapi.domain.universe.dto.UniverseResponse;
import com.korit.feelioapi.domain.universe.dto.UniverseTotalDto;
import com.korit.feelioapi.domain.universe.mapper.UniverseMapper;
import com.korit.feelioapi.global.exception.BusinessException;
import com.korit.feelioapi.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UniverseServiceTest {

    @Mock private UniverseMapper universeMapper;

    /**
     * 문장 생성은 A7-3 에서 ScenarioNarrator 로 분리됐다. 여기서는 실물(규칙기반)을 넣어
     * 기존 시나리오 계산 결과가 그대로인지 함께 확인한다 — mock 을 넣으면 narration 이 null 이 된다.
     */
    @Spy private ScenarioNarrator scenarioNarrator = new RuleBasedScenarioNarrator();

    @InjectMocks private UniverseService universeService;

    private GoalRow goal(long id, long userId, int target, int current) {
        return goal(id, userId, target, current, "제주도 여행");
    }

    private GoalRow goal(long id, long userId, int target, int current, String name) {
        return new GoalRow(id, userId, name, target, current);
    }

    @Test
    void 두_시나리오를_계산한다_REDUCED가_더_빠르다() {
        // remaining=20,000,000, income=3,000,000, expense=2,000,000, focus=1,000,000
        when(universeMapper.findGoalById(1L)).thenReturn(goal(1L, 100L, 20_000_000, 0));
        when(universeMapper.findLatestActivityMonth(100L)).thenReturn(new MonthKey(2026, 7));
        when(universeMapper.findMonthlyTotals(100L, 2026, 7))
                .thenReturn(new UniverseTotalDto(3_000_000L, 2_000_000L));
        when(universeMapper.findTopCategory(100L, 2026, 7))
                .thenReturn(new TopCategoryDto(2L, "배달", 1_000_000L));

        UniverseResponse res = universeService.simulate(100L, 1L);

        assertThat(res.reductionRate()).isEqualTo(0.5);
        assertThat(res.topCategory().name()).isEqualTo("배달");
        assertThat(res.scenarios()).hasSize(2);

        ScenarioDto current = res.scenarios().get(0);
        ScenarioDto reduced = res.scenarios().get(1);
        // CURRENT: saving 1,000,000 → ceil(20) = 20
        assertThat(current.key()).isEqualTo("CURRENT");
        assertThat(current.monthsToGoal()).isEqualTo(20);
        // REDUCED: expense 2,000,000 - round(1,000,000*0.5)=1,500,000 → saving 1,500,000 → ceil(13.33)=14
        assertThat(reduced.key()).isEqualTo("REDUCED");
        assertThat(reduced.monthlyExpense()).isEqualTo(1_500_000L);
        assertThat(reduced.monthsToGoal()).isEqualTo(14);
        assertThat(reduced.title()).isEqualTo("배달 소비를 줄이면");
        assertThat(reduced.estimatedAchieveDate()).isNotNull();

        // 개월은 올림이라 한 달 안쪽에서 두 시나리오가 같은 값이 된다. 일수는 그 차이를 담아야 한다.
        // 20개월 → ceil(20 * 30) = 600일, 13.33개월 → ceil(13.33 * 30) = 400일
        assertThat(current.daysToGoal()).isEqualTo(600);
        assertThat(reduced.daysToGoal()).isEqualTo(400);
    }

    @Test
    void 한_달_안쪽이면_개월은_같아도_일수는_다르다() {
        // 남은 1,600,000 / 저축 1,800,000 = 0.89개월, 감축 저축 2,000,000 = 0.8개월 → 둘 다 올림 1개월
        when(universeMapper.findGoalById(1L)).thenReturn(goal(1L, 100L, 1_600_000, 0));
        when(universeMapper.findLatestActivityMonth(100L)).thenReturn(new MonthKey(2026, 7));
        when(universeMapper.findMonthlyTotals(100L, 2026, 7))
                .thenReturn(new UniverseTotalDto(2_400_000L, 600_000L));
        when(universeMapper.findTopCategory(100L, 2026, 7))
                .thenReturn(new TopCategoryDto(2L, "배달", 400_000L));

        UniverseResponse res = universeService.simulate(100L, 1L);
        ScenarioDto current = res.scenarios().get(0);
        ScenarioDto reduced = res.scenarios().get(1);

        // 더 모으는 쪽이 같은 시점에 닿는 것처럼 보이던 자리다.
        assertThat(current.monthsToGoal()).isEqualTo(reduced.monthsToGoal());
        assertThat(reduced.monthlySaving()).isGreaterThan(current.monthlySaving());
        assertThat(reduced.daysToGoal()).isLessThan(current.daysToGoal());
    }

    @Test
    void 저축여력이_없으면_도달불가_null() {
        // 지출이 수입 이상 → saving 0 → monthsToGoal null
        when(universeMapper.findGoalById(1L)).thenReturn(goal(1L, 100L, 2_000_000, 0));
        when(universeMapper.findLatestActivityMonth(100L)).thenReturn(new MonthKey(2026, 7));
        when(universeMapper.findMonthlyTotals(100L, 2026, 7))
                .thenReturn(new UniverseTotalDto(1_000_000L, 1_200_000L));
        when(universeMapper.findTopCategory(100L, 2026, 7)).thenReturn(null);

        UniverseResponse res = universeService.simulate(100L, 1L);

        assertThat(res.topCategory()).isNull();
        assertThat(res.scenarios().get(0).monthsToGoal()).isNull();
        assertThat(res.scenarios().get(0).estimatedAchieveDate()).isNull();
    }

    @Test
    void 거래가_없으면_지출0_카테고리_null() {
        when(universeMapper.findGoalById(1L)).thenReturn(goal(1L, 100L, 2_000_000, 0));
        when(universeMapper.findLatestActivityMonth(100L)).thenReturn(new MonthKey(null, null));

        UniverseResponse res = universeService.simulate(100L, 1L);

        assertThat(res.monthlyIncome()).isZero();
        assertThat(res.monthlyExpense()).isZero();
        assertThat(res.topCategory()).isNull();
        assertThat(res.scenarios().get(1).title()).isEqualTo("전체 소비를 줄이면");
    }

    @Test
    void goalId_누락은_VALIDATION_ERROR() {
        assertThatThrownBy(() -> universeService.simulate(100L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void 없는_목표는_NOT_FOUND() {
        when(universeMapper.findGoalById(9L)).thenReturn(null);

        assertThatThrownBy(() -> universeService.simulate(100L, 9L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void 타인_목표는_FORBIDDEN() {
        when(universeMapper.findGoalById(1L)).thenReturn(goal(1L, 200L, 2_000_000, 0));

        assertThatThrownBy(() -> universeService.simulate(100L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    // --- A7-3: narration 생성 분리 ---

    @Test
    void 계산된_개월수가_문장생성기로_그대로_넘어간다() {
        ScenarioNarrator narrator = mock(ScenarioNarrator.class);
        when(narrator.narrate(any())).thenReturn(List.of(List.of("현행 문장"), List.of("감축 문장")));

        when(universeMapper.findGoalById(1L)).thenReturn(goal(1L, 100L, 20_000_000, 0));
        when(universeMapper.findLatestActivityMonth(100L)).thenReturn(new MonthKey(2026, 7));
        when(universeMapper.findMonthlyTotals(100L, 2026, 7))
                .thenReturn(new UniverseTotalDto(3_000_000L, 2_000_000L));
        when(universeMapper.findTopCategory(100L, 2026, 7))
                .thenReturn(new TopCategoryDto(2L, "배달", 1_000_000L));

        UniverseResponse res = new UniverseService(universeMapper, narrator).simulate(100L, 1L);

        ArgumentCaptor<NarrationContext> captor = ArgumentCaptor.forClass(NarrationContext.class);
        verify(narrator).narrate(captor.capture());
        NarrationContext context = captor.getValue();

        // 숫자는 서비스가 계산해 넘긴다. 모델이 다시 계산하면 화면 숫자와 어긋난다.
        assertThat(context.goalName()).isEqualTo("제주도 여행");
        assertThat(context.focusCategoryName()).isEqualTo("배달");
        assertThat(context.currentMonths()).isEqualTo(20);
        assertThat(context.reducedMonths()).isEqualTo(14);

        // 돌려받은 문장이 CURRENT·REDUCED 순서대로 붙는다.
        assertThat(res.scenarios().get(0).narrations()).containsExactly("현행 문장");
        assertThat(res.scenarios().get(1).narrations()).containsExactly("감축 문장");
    }

    @Test
    void 규칙기반_문장도_목표_이름을_부른다() {
        when(universeMapper.findGoalById(1L)).thenReturn(goal(1L, 100L, 20_000_000, 0));
        when(universeMapper.findLatestActivityMonth(100L)).thenReturn(new MonthKey(2026, 7));
        when(universeMapper.findMonthlyTotals(100L, 2026, 7))
                .thenReturn(new UniverseTotalDto(3_000_000L, 2_000_000L));
        when(universeMapper.findTopCategory(100L, 2026, 7))
                .thenReturn(new TopCategoryDto(2L, "배달", 1_000_000L));

        UniverseResponse res = universeService.simulate(100L, 1L);

        // GPT 가 죽으면 이 문장이 그대로 화면에 나간다. 폴백이 '목표'라고만 말하면
        // 어떤 목표 이야기인지 알 수 없어, AI 를 붙인 의미가 폴백에서 사라진다.
        assertThat(res.scenarios().get(0).narrations().get(0))
                .isEqualTo("지금 속도라면 약 20개월 뒤 제주도 여행에 닿아요.");
        assertThat(res.scenarios().get(1).narrations().get(0))
                .isEqualTo("이렇게 줄이면 약 14개월 뒤 제주도 여행 도착, 6개월 빨라져요.");

        // 롤링용 나머지 코멘트에도 목표 이름이 살아 있어야 한다.
        assertThat(res.scenarios().get(0).narrations()).anyMatch(line -> line.contains("제주도 여행"));
        assertThat(res.scenarios().get(1).narrations()).anyMatch(line -> line.contains("제주도 여행"));
    }

    @Test
    void 롤링_코멘트는_전부_소비_숫자_이야기다() {
        // income 3,000,000 / expense 2,000,000 → 현행 저축 1,000,000
        // 배달 1,000,000 의 절반을 줄여 지출 1,500,000 → 감축 저축 1,500,000, 매달 +500,000
        when(universeMapper.findGoalById(1L)).thenReturn(goal(1L, 100L, 20_000_000, 0));
        when(universeMapper.findLatestActivityMonth(100L)).thenReturn(new MonthKey(2026, 7));
        when(universeMapper.findMonthlyTotals(100L, 2026, 7))
                .thenReturn(new UniverseTotalDto(3_000_000L, 2_000_000L));
        when(universeMapper.findTopCategory(100L, 2026, 7))
                .thenReturn(new TopCategoryDto(2L, "배달", 1_000_000L));

        UniverseResponse res = universeService.simulate(100L, 1L);

        // 소비 시뮬레이션 화면이다. 응원·덕담이 아니라 근거가 되는 금액이 나와야 한다.
        // 카드에 이미 적힌 값(이번 달 지출·도달 개월)은 되풀이하지 않는다 — 넘겨 읽을 이유가 없어진다.
        assertThat(res.scenarios().get(0).narrations())
                .containsExactly(
                        "지금 속도라면 약 20개월 뒤 제주도 여행에 닿아요.",
                        "제주도 여행까지 20,000,000원 남았어요.",
                        "지금은 매달 1,000,000원씩 모으고 있어요.");
        assertThat(res.scenarios().get(1).narrations())
                .containsExactly(
                        "이렇게 줄이면 약 14개월 뒤 제주도 여행 도착, 6개월 빨라져요.",
                        "배달 지출을 줄이면 매달 모으는 돈이 1,500,000원이 돼요.",
                        "그만큼 제주도 여행 도착이 앞당겨져요.");

        // 카드가 크게 보여주는 이번 달 지출 금액은 문장에 다시 나오지 않는다.
        assertThat(res.scenarios().get(0).narrations()).noneMatch(line -> line.contains("2,000,000원"));
    }

    @Test
    void 목표_이름이_비면_목표라고만_부른다() {
        when(universeMapper.findGoalById(1L)).thenReturn(goal(1L, 100L, 20_000_000, 0, "  "));
        when(universeMapper.findLatestActivityMonth(100L)).thenReturn(new MonthKey(2026, 7));
        when(universeMapper.findMonthlyTotals(100L, 2026, 7))
                .thenReturn(new UniverseTotalDto(3_000_000L, 2_000_000L));
        when(universeMapper.findTopCategory(100L, 2026, 7))
                .thenReturn(new TopCategoryDto(2L, "배달", 1_000_000L));

        UniverseResponse res = universeService.simulate(100L, 1L);

        // 이름이 없다고 "  에 닿아요" 같은 문장이 나가면 안 된다.
        assertThat(res.scenarios().get(0).narrations().get(0))
                .isEqualTo("지금 속도라면 약 20개월 뒤 목표에 닿아요.");
    }
}
