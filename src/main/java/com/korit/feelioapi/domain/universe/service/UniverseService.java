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
        TopCategoryDto topCategory = null;

        MonthKey latest = universeMapper.findLatestActivityMonth(userId);
        if (latest != null && latest.year() != null) {
            UniverseTotalDto totals = universeMapper.findMonthlyTotals(userId, latest.year(), latest.month());
            income = totals.monthlyIncome();
            expense = totals.monthlyExpense();
            topCategory = universeMapper.findTopCategory(userId, latest.year(), latest.month());
        }

        long focusAmount = topCategory == null ? 0L : topCategory.monthlyAmount();
        long reducedExpense = Math.max(0L, expense - Math.round(focusAmount * REDUCTION_RATE));
        long remaining = (long) goal.targetAmount() - goal.currentAmount();

        Projection current = project(income, expense, remaining);
        long savedByReduction = expense - reducedExpense;

        // REDUCED는 줄인 소비를 목표 저금으로 옮긴 미래다. 현재가 적자여도 실제 감축액을
        // 0원으로 없애지 않고 현재 저축 여력에 더해 목표 도달 기간을 계산한다.
        Projection reduced = projectWithSaving(current.saving() + savedByReduction, remaining);

        // 숫자를 모두 확정한 뒤 문장을 한 번에 받는다. 두 문장은 서로를 참조해야(몇 개월 빨라지는지) 자연스럽다.
        // 금액도 함께 넘긴다 — 숫자를 안 주면 모델이 소비와 무관한 말로 칸을 채운다.
        List<List<String>> narrations = scenarioNarrator.narrate(new NarrationContext(
                goal.name(),
                topCategory == null ? null : topCategory.name(),
                expense,
                Math.max(0L, remaining),
                current.saving(),
                // '아낀 금액'은 저축액 차이가 아니라 실제 지출 감소액이다. 두 시나리오 모두
                // 적자라 saving이 0이어도 사용자가 줄인 소비액은 사라지면 안 된다.
                savedByReduction,
                reduced.saving(),
                current.months(),
                reduced.months(),
                current.days(),
                reduced.days()));

        ScenarioDto currentScenario = new ScenarioDto("CURRENT", "지금처럼 쓴다면",
                expense, current.saving(), current.months(), current.days(), current.achieveDate(), narrations.get(0));

        /*
         * 제목에는 카테고리를 넣지 않는다.
         *
         * "여행 소비를 줄이면" 처럼 쓰면 이 우주가 여행 전용 시뮬레이션인 것처럼 읽힌다.
         * 이 화면이 보여주는 건 "소비를 줄이면 목표에 언제 닿는가" 하나이고, 어떤 항목부터
         * 줄일지는 그 안에서 말랑이 문구(narration)가 이미 이름을 불러 짚어 준다.
         * 우주의 이름과 그 안의 조언은 다른 층위다.
         */
        String reducedTitle = "소비를 줄이면";
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

        return projectWithSaving(saving, remaining);
    }

    private Projection projectWithSaving(long saving, long remaining) {

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
