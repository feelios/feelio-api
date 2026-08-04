package com.korit.feelioapi.domain.summary.service;

/** 월 지출 집계만 받아 홈 멘트를 만드는 교체 지점. 개인 식별 정보는 전달하지 않는다. */
public interface SummaryAiCommentGenerator {
    String generate(int year, int month, long currentExpense, long previousExpense);
}
