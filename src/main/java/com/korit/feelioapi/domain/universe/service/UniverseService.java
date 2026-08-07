package com.korit.feelioapi.domain.universe.service;

import com.korit.feelioapi.domain.universe.dto.FocusEmotionDto;
import com.korit.feelioapi.domain.universe.dto.GoalRow;
import com.korit.feelioapi.domain.universe.dto.GoalSummaryDto;
import com.korit.feelioapi.domain.universe.dto.MonthKey;
import com.korit.feelioapi.domain.universe.dto.ScenarioDto;
import com.korit.feelioapi.domain.universe.dto.UniverseResponse;
import com.korit.feelioapi.domain.universe.dto.UniverseTotalDto;
import com.korit.feelioapi.domain.universe.mapper.UniverseMapper;
import com.korit.feelioapi.global.exception.BusinessException;
import com.korit.feelioapi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;

/**
 * 평행우주 시뮬 (API-CONTRACT §9). 대표(또는 지정) 목표에 대해 CURRENT/REDUCED 두 미래를 비교한다.
 * "감정소비"는 소비가 가장 몰린 한 감정(focusEmotion)이며, REDUCED 는 그 감정 지출만 reductionRate 만큼 줄인다.
 * 기준 월 = 거래가 있는 가장 최근 연·월. 누수율(비율 지표)은 사용하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class UniverseService {

    private static final double REDUCTION_RATE = 0.5;

    private final UniverseMapper universeMapper;
    private final ScenarioNarrator scenarioNarrator;

    /**
     * 트랜잭션을 걸지 않는다 — 문장 생성이 GPT 호출을 포함할 수 있어(A7-3), 트랜잭션으로 감싸면
     * 외부 호출이 끝날 때까지 DB 커넥션을 쥐고 있게 된다(풀 크기 5). 조회는 모두 읽기 전용이라
     * 매퍼 호출이 각자 커넥션을 빌렸다 바로 반납하는 편이 낫다.
     */
    public UniverseResponse simulate(Long userId, Long goalId) {
        if (goalId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "goalId는 필수입니다.");
        }
        GoalRow goal = universeMapper.findGoalById(goalId);
        if (goal == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!goal.userId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        long income = 0L;
        long expense = 0L;
        FocusEmotionDto focusEmotion = null;

        MonthKey latest = universeMapper.findLatestActivityMonth(userId);
        if (latest != null && latest.year() != null) {
            UniverseTotalDto totals = universeMapper.findMonthlyTotals(userId, latest.year(), latest.month());
            income = totals.monthlyIncome();
            expense = totals.monthlyExpense();
            focusEmotion = universeMapper.findFocusEmotion(userId, latest.year(), latest.month());
        }

        long focusAmount = focusEmotion == null ? 0L : focusEmotion.monthlyAmount();
        long reducedExpense = Math.max(0L, expense - Math.round(focusAmount * REDUCTION_RATE));
        long remaining = (long) goal.targetAmount() - goal.currentAmount();

        Projection current = project(income, expense, remaining);
        Projection reduced = project(income, reducedExpense, remaining);

        // 숫자를 모두 확정한 뒤 문장을 한 번에 받는다. 두 문장은 서로를 참조해야(몇 개월 빨라지는지) 자연스럽다.
        List<List<String>> narrations = scenarioNarrator.narrate(new NarrationContext(
                goal.name(),
                focusEmotion == null ? null : focusEmotion.name(),
                current.months(),
                reduced.months()));

        ScenarioDto currentScenario = new ScenarioDto("CURRENT", "지금처럼 쓴다면",
                expense, current.saving(), current.months(), current.achieveDate(), narrations.get(0));

        String reducedTitle = (focusEmotion != null ? focusEmotion.name() : "감정") + " 소비를 줄이면";
        ScenarioDto reducedScenario = new ScenarioDto("REDUCED", reducedTitle,
                reducedExpense, reduced.saving(), reduced.months(), reduced.achieveDate(), narrations.get(1));

        GoalSummaryDto goalDto = new GoalSummaryDto(
                goal.goalId(), goal.name(), goal.targetAmount(), goal.currentAmount());

        return new UniverseResponse(goalDto, income, expense, focusEmotion, REDUCTION_RATE,
                List.of(currentScenario, reducedScenario));
    }

    /** 시나리오의 숫자 부분. months·achieveDate 는 도달 불가(월 저축 ≤ 0) 시 null. */
    private record Projection(long saving, Integer months, String achieveDate) {
    }

    /** 계약 §9: 월 저축 = 수입 − 지출(≥0), 도달 개월 = ceil(남은액/저축), 저축 ≤ 0 이면 도달 불가(null). */
    private Projection project(long income, long monthlyExpense, long remaining) {
        long saving = Math.max(0L, income - monthlyExpense);

        if (remaining <= 0) {
            return new Projection(saving, 0, YearMonth.now().toString());
        }
        if (saving <= 0) {
            return new Projection(saving, null, null);
        }
        int months = (int) Math.ceil((double) remaining / saving);
        return new Projection(saving, months, YearMonth.now().plusMonths(months).toString());
    }
}
