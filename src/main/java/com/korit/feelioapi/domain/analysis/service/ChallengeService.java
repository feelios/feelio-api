package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.CategoryStatDto;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ChallengeService {

    private final RuleBasedInsightCardGenerator fallback;

    public ChallengeService(RuleBasedInsightCardGenerator fallback) {
        this.fallback = fallback;
    }

    public String generate(List<CategoryStatDto> weeklyCategories) {
        if (weeklyCategories == null || weeklyCategories.isEmpty()) {
            return fallback.challenge(null);
        }
        String topCategory = weeklyCategories.get(0).name();
        return fallback.challenge(topCategory);
    }
}
