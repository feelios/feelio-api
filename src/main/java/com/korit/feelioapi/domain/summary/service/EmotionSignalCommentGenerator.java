package com.korit.feelioapi.domain.summary.service;

import java.util.List;

public interface EmotionSignalCommentGenerator {
    String generate(int year, int month, List<EmotionSignal> signals);
}
