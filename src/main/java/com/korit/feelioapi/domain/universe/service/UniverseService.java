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
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
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

        ScenarioDto current = buildScenario("CURRENT", "지금처럼 쓴다면", income, expense, remaining, null);
        String reducedTitle = (focusEmotion != null ? focusEmotion.name() : "감정") + " 소비를 줄이면";
        ScenarioDto reduced = buildScenario("REDUCED", reducedTitle, income, reducedExpense, remaining,
                current.monthsToGoal());

        GoalSummaryDto goalDto = new GoalSummaryDto(
                goal.goalId(), goal.name(), goal.targetAmount(), goal.currentAmount());

        return new UniverseResponse(goalDto, income, expense, focusEmotion, REDUCTION_RATE,
                List.of(current, reduced));
    }

    /** 시나리오 계산: 월 저축 = 수입 − 지출(≥0), 도달 개월 = ceil(남은액/저축), 저축 ≤ 0 이면 도달 불가(null). */
    private ScenarioDto buildScenario(String key, String title, long income, long monthlyExpense,
                                      long remaining, Integer currentMonths) {
        long saving = Math.max(0L, income - monthlyExpense);

        Integer months;
        String achieveDate;
        if (remaining <= 0) {
            months = 0;
            achieveDate = YearMonth.now().toString();
        } else if (saving <= 0) {
            months = null;
            achieveDate = null;
        } else {
            months = (int) Math.ceil((double) remaining / saving);
            achieveDate = YearMonth.now().plusMonths(months).toString();
        }

        return new ScenarioDto(key, title, monthlyExpense, saving, months, achieveDate,
                narrate(key, months, currentMonths));
    }

    private String narrate(String key, Integer months, Integer currentMonths) {
        if (months != null && months == 0) {
            return "이미 목표 금액을 모았어요.";
        }
        boolean isReduced = "REDUCED".equals(key);
        if (months == null) {
            return isReduced ? "지출을 더 줄이면 목표에 다가갈 수 있어요."
                    : "지금 소비 흐름으로는 목표 도달이 어려워요. 조금 줄여볼까요?";
        }
        if (isReduced && currentMonths != null && currentMonths > months) {
            return String.format("이렇게 줄이면 약 %d개월 뒤 도착, %d개월 빨라져요.",
                    months, currentMonths - months);
        }
        return String.format("지금 속도라면 약 %d개월 뒤 목표에 닿아요.", months);
    }
}
