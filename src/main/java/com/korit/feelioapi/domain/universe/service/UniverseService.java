package com.korit.feelioapi.domain.universe.service;

import com.korit.feelioapi.domain.universe.dto.TopCategoryDto;
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
 * REDUCED 는 소비가 가장 몰린 카테고리(topCategory) 지출만 reductionRate 만큼 줄인 미래다.
 * 기준을 감정에서 카테고리로 옮겼다 — "평온 소비를 줄이면"은 왜 그 감정인지도, 무엇을 줄여야 하는지도
 * 화면에서 설명되지 않았다. "배달 소비를 줄이면"은 사용자가 바로 행동으로 옮길 수 있다.
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
        long variableExpense = 0L;
        TopCategoryDto topCategory = null;

        MonthKey latest = universeMapper.findLatestActivityMonth(userId);
        if (latest != null && latest.year() != null) {
            UniverseTotalDto totals = universeMapper.findMonthlyTotals(userId, latest.year(), latest.month());
            income = totals.monthlyIncome();
            expense = totals.monthlyExpense();
            variableExpense = universeMapper.findVariableExpense(userId, latest.year(), latest.month());
            topCategory = universeMapper.findTopCategory(userId, latest.year(), latest.month());
        }

        // 줄이는 대상은 변동비 전체다. 최상위 카테고리 하나만 줄이면 전체 지출의 10~20%뿐이라
        // 두 우주의 도달 시점이 늘 같은 개월로 뭉개졌다 — 볼 이유가 없는 화면이 된다.
        long focusAmount = variableExpense;
        long reducedExpense = Math.max(0L, expense - Math.round(focusAmount * REDUCTION_RATE));
        long remaining = (long) goal.targetAmount() - goal.currentAmount();

        Projection current = project(income, expense, remaining);
        Projection reduced = project(income, reducedExpense, remaining);

        // 숫자를 모두 확정한 뒤 문장을 한 번에 받는다. 두 문장은 서로를 참조해야(몇 개월 빨라지는지) 자연스럽다.
        // 금액도 함께 넘긴다 — 숫자를 안 주면 모델이 소비와 무관한 말로 칸을 채운다.
        List<List<String>> narrations = scenarioNarrator.narrate(new NarrationContext(
                goal.name(),
                topCategory == null ? null : topCategory.name(),
                expense,
                Math.max(0L, remaining),
                current.saving(),
                reduced.saving() - current.saving(),
                reduced.saving(),
                current.months(),
                reduced.months()));

        ScenarioDto currentScenario = new ScenarioDto("CURRENT", "지금처럼 쓴다면",
                expense, current.saving(), current.months(), current.days(), current.achieveDate(), narrations.get(0));

        // 대상은 변동비 전체지만, 제목은 가장 큰 항목을 앞세운다 — "변동비"보다 "패션,미용부터"가
        // 무엇을 해야 하는지 바로 말해준다. 고정비만 있거나 지출이 없으면 대표 항목이 없다.
        String reducedTitle = topCategory != null && variableExpense > 0
                ? topCategory.name() + "부터 절반만 쓴다면"
                : "덜 쓴다면";
        ScenarioDto reducedScenario = new ScenarioDto("REDUCED", reducedTitle,
                reducedExpense, reduced.saving(), reduced.months(), reduced.days(), reduced.achieveDate(), narrations.get(1));

        GoalSummaryDto goalDto = new GoalSummaryDto(
                goal.goalId(), goal.name(), goal.targetAmount(), goal.currentAmount());

        return new UniverseResponse(goalDto, income, expense, topCategory, REDUCTION_RATE,
                List.of(currentScenario, reducedScenario));
    }

    /** 한 달을 며칠로 볼지. 일수는 어림값이라 30 으로 고정한다 — 달마다 바뀌면 비교가 흔들린다. */
    private static final int DAYS_PER_MONTH = 30;

    /** 시나리오의 숫자 부분. months·days·achieveDate 는 도달 불가(월 저축 ≤ 0) 시 null. */
    private record Projection(long saving, Integer months, Integer days, String achieveDate) {
    }

    /** 계약 §9: 월 저축 = 수입 − 지출(≥0), 도달 개월 = ceil(남은액/저축), 저축 ≤ 0 이면 도달 불가(null). */
    private Projection project(long income, long monthlyExpense, long remaining) {
        long saving = Math.max(0L, income - monthlyExpense);

        if (remaining <= 0) {
            return new Projection(saving, 0, 0, YearMonth.now().toString());
        }
        if (saving <= 0) {
            return new Projection(saving, null, null, null);
        }
        double exactMonths = (double) remaining / saving;
        int months = (int) Math.ceil(exactMonths);
        // 개월은 올림이라 한 달 안쪽에서 두 시나리오가 같은 값이 된다. 일수는 그 차이를 담는다.
        int days = (int) Math.ceil(exactMonths * DAYS_PER_MONTH);
        return new Projection(saving, months, days, YearMonth.now().plusMonths(months).toString());
    }
}
