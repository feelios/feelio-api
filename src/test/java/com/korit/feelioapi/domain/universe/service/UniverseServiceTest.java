package com.korit.feelioapi.domain.universe.service;

import com.korit.feelioapi.domain.universe.dto.FocusEmotionDto;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UniverseServiceTest {

    @Mock private UniverseMapper universeMapper;

    @InjectMocks private UniverseService universeService;

    private GoalRow goal(long id, long userId, int target, int current) {
        return new GoalRow(id, userId, "제주도 여행", target, current);
    }

    @Test
    void 두_시나리오를_계산한다_REDUCED가_더_빠르다() {
        // remaining=20,000,000, income=3,000,000, expense=2,000,000, focus=1,000,000
        when(universeMapper.findGoalById(1L)).thenReturn(goal(1L, 100L, 20_000_000, 0));
        when(universeMapper.findLatestActivityMonth(100L)).thenReturn(new MonthKey(2026, 7));
        when(universeMapper.findMonthlyTotals(100L, 2026, 7))
                .thenReturn(new UniverseTotalDto(3_000_000L, 2_000_000L));
        when(universeMapper.findFocusEmotion(100L, 2026, 7))
                .thenReturn(new FocusEmotionDto(2L, "설렘", "#F28AB7", 1_000_000L));

        UniverseResponse res = universeService.simulate(100L, 1L);

        assertThat(res.reductionRate()).isEqualTo(0.5);
        assertThat(res.focusEmotion().name()).isEqualTo("설렘");
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
        assertThat(reduced.title()).isEqualTo("설렘 소비를 줄이면");
        assertThat(reduced.estimatedAchieveDate()).isNotNull();
    }

    @Test
    void 저축여력이_없으면_도달불가_null() {
        // 지출이 수입 이상 → saving 0 → monthsToGoal null
        when(universeMapper.findGoalById(1L)).thenReturn(goal(1L, 100L, 2_000_000, 0));
        when(universeMapper.findLatestActivityMonth(100L)).thenReturn(new MonthKey(2026, 7));
        when(universeMapper.findMonthlyTotals(100L, 2026, 7))
                .thenReturn(new UniverseTotalDto(1_000_000L, 1_200_000L));
        when(universeMapper.findFocusEmotion(100L, 2026, 7)).thenReturn(null);

        UniverseResponse res = universeService.simulate(100L, 1L);

        assertThat(res.focusEmotion()).isNull();
        assertThat(res.scenarios().get(0).monthsToGoal()).isNull();
        assertThat(res.scenarios().get(0).estimatedAchieveDate()).isNull();
    }

    @Test
    void 거래가_없으면_지출0_focus_null() {
        when(universeMapper.findGoalById(1L)).thenReturn(goal(1L, 100L, 2_000_000, 0));
        when(universeMapper.findLatestActivityMonth(100L)).thenReturn(new MonthKey(null, null));

        UniverseResponse res = universeService.simulate(100L, 1L);

        assertThat(res.monthlyIncome()).isZero();
        assertThat(res.monthlyExpense()).isZero();
        assertThat(res.focusEmotion()).isNull();
        assertThat(res.scenarios().get(1).title()).isEqualTo("감정 소비를 줄이면");
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
}
