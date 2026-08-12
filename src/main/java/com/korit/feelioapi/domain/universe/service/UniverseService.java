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
    public UniverseResponse simulate(Long userId, Long goalId, List<Long> categoryIds) {
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
        List<TopCategoryDto> categories = List.of();

        MonthKey latest = universeMapper.findLatestActivityMonth(userId);
        if (latest != null && latest.year() != null) {
            UniverseTotalDto totals = universeMapper.findMonthlyTotals(userId, latest.year(), latest.month());
            income = totals.monthlyIncome();
            expense = totals.monthlyExpense();
            categories = universeMapper.findExpenseCategories(userId, latest.year(), latest.month());
        }

        List<TopCategoryDto> focusCategories = resolveFocus(categories, categoryIds);
        TopCategoryDto topCategory = focusCategories.isEmpty() ? null : focusCategories.get(0);
        long focusAmount = focusCategories.stream().mapToLong(TopCategoryDto::monthlyAmount).sum();
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

        String focusNames = focusCategories.stream().map(TopCategoryDto::name).collect(java.util.stream.Collectors.joining("·"));
        String reducedTitle = (focusCategories.isEmpty() ? "전체" : focusNames) + " 소비를 줄이면";
        ScenarioDto reducedScenario = new ScenarioDto("REDUCED", reducedTitle,
                reducedExpense, reduced.saving(), reduced.months(), reduced.days(), reduced.achieveDate(), narrations.get(1));

        GoalSummaryDto goalDto = new GoalSummaryDto(
                goal.goalId(), goal.name(), goal.targetAmount(), goal.currentAmount());

        return new UniverseResponse(goalDto, income, expense, topCategory, categories, focusCategories, REDUCTION_RATE,
                List.of(currentScenario, reducedScenario));
    }

    /** 한 달을 며칠로 볼지. 일수는 어림값이라 30 으로 고정한다 — 달마다 바뀌면 비교가 흔들린다. */
    private static final int DAYS_PER_MONTH = 30;

    /**
     * 줄일 카테고리를 정한다.
     *
     * 고른 게 없으면 가장 많이 쓴 것 하나가 기본값이다 — 예전 동작(topCategory)과 같다.
     * 고른 게 있으면 그 달에 실제 지출이 있는 것만 남긴다. 없는 id 를 보내도 조용히 무시하고,
     * 전부 걸러지면 기본값으로 되돌린다 — 시나리오가 비면 화면이 설명할 게 없어진다.
     */
    private List<TopCategoryDto> resolveFocus(List<TopCategoryDto> categories, List<Long> categoryIds) {
        if (categories.isEmpty()) {
            return List.of();
        }
        if (categoryIds == null || categoryIds.isEmpty()) {
            return List.of(categories.get(0));
        }
        List<TopCategoryDto> picked = categories.stream()
                .filter(category -> categoryIds.contains(category.categoryId()))
                .toList();
        return picked.isEmpty() ? List.of(categories.get(0)) : picked;
    }

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
