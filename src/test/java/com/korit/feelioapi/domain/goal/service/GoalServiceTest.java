package com.korit.feelioapi.domain.goal.service;

import com.korit.feelioapi.domain.goal.dto.GoalListResponse;
import com.korit.feelioapi.domain.goal.dto.GoalRequest;
import com.korit.feelioapi.domain.goal.dto.GoalResponse;
import com.korit.feelioapi.domain.goal.entity.Goal;
import com.korit.feelioapi.domain.goal.mapper.GoalMapper;
import com.korit.feelioapi.global.exception.BusinessException;
import com.korit.feelioapi.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GoalService 단위 테스트 (계약 §7). GoalMapper 목킹.
 */
@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    @Mock private GoalMapper goalMapper;

    @InjectMocks private GoalService goalService;

    private Goal goal(long id, long userId, String name, boolean isMain) {
        Goal g = new Goal();
        g.setGoalId(id);
        g.setUserId(userId);
        g.setName(name);
        g.setTargetAmount(2000000);
        g.setCurrentAmount(0);
        g.setIsMain(isMain);
        g.setStatus("ACTIVE");
        return g;
    }

    private GoalRequest request(String name, Integer targetAmount, Boolean isMain) {
        return new GoalRequest(name, targetAmount, null, null, isMain);
    }

    @Test
    void 목표_목록을_반환한다() {
        when(goalMapper.findGoalsByUserId(1L))
                .thenReturn(List.of(goal(1L, 1L, "제주도 여행", true), goal(2L, 1L, "노트북", false)));

        GoalListResponse response = goalService.getGoals(1L);

        assertThat(response.goals()).hasSize(2);
        assertThat(response.goals().get(0).isMain()).isTrue();
    }

    @Test
    void 대표목표로_생성하면_기존대표를_해제하고_삽입한다() {
        doAnswer(inv -> {
            Goal g = inv.getArgument(0);
            g.setGoalId(10L);
            return null;
        }).when(goalMapper).insertGoal(any(Goal.class));
        when(goalMapper.findById(10L)).thenReturn(goal(10L, 1L, "제주도 여행", true));

        GoalResponse response = goalService.createGoal(1L, request("제주도 여행", 2000000, true));

        assertThat(response.goalId()).isEqualTo(10L);
        assertThat(response.isMain()).isTrue();
        verify(goalMapper).clearMainFlag(1L);
        verify(goalMapper).insertGoal(any(Goal.class));
    }

    @Test
    void 대표가_아니면_기존대표를_해제하지_않는다() {
        doAnswer(inv -> {
            Goal g = inv.getArgument(0);
            g.setGoalId(11L);
            return null;
        }).when(goalMapper).insertGoal(any(Goal.class));
        when(goalMapper.findById(11L)).thenReturn(goal(11L, 1L, "노트북", false));

        goalService.createGoal(1L, request("노트북", 1500000, false));

        verify(goalMapper, never()).clearMainFlag(1L);
    }

    @Test
    void 목표를_수정한다() {
        when(goalMapper.findById(10L)).thenReturn(goal(10L, 1L, "제주도 여행", false));

        GoalResponse response = goalService.updateGoal(1L, 10L, request("제주도 여행 2", 3000000, false));

        assertThat(response.goalId()).isEqualTo(10L);
        verify(goalMapper).updateGoal(any(Goal.class));
        verify(goalMapper, never()).clearMainFlag(1L);
    }

    @Test
    void 수정시_대표로_바꾸면_기존대표를_해제한다() {
        when(goalMapper.findById(10L)).thenReturn(goal(10L, 1L, "제주도 여행", true));

        goalService.updateGoal(1L, 10L, request("제주도 여행", 2000000, true));

        verify(goalMapper).clearMainFlag(1L);
        verify(goalMapper).updateGoal(any(Goal.class));
    }

    @Test
    void 없는_목표_수정은_NOT_FOUND() {
        when(goalMapper.findById(99L)).thenReturn(null);

        assertThatThrownBy(() -> goalService.updateGoal(1L, 99L, request("x", 1000, false)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);

        verify(goalMapper, never()).updateGoal(any(Goal.class));
    }

    @Test
    void 타인_목표_수정은_FORBIDDEN() {
        when(goalMapper.findById(10L)).thenReturn(goal(10L, 2L, "남의 목표", false));

        assertThatThrownBy(() -> goalService.updateGoal(1L, 10L, request("x", 1000, false)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);

        verify(goalMapper, never()).updateGoal(any(Goal.class));
    }

    @Test
    void 목표를_삭제하고_deleted_true를_반환한다() {
        when(goalMapper.findById(10L)).thenReturn(goal(10L, 1L, "제주도 여행", false));

        var response = goalService.deleteGoal(1L, 10L);

        assertThat(response.deleted()).isTrue();
        verify(goalMapper).deleteGoal(10L);
    }

    @Test
    void 타인_목표_삭제는_FORBIDDEN() {
        when(goalMapper.findById(10L)).thenReturn(goal(10L, 2L, "남의 목표", false));

        assertThatThrownBy(() -> goalService.deleteGoal(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);

        verify(goalMapper, never()).deleteGoal(10L);
    }
}
