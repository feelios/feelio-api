package com.korit.feelioapi.domain.analysis.service;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class AiTest {
    @Autowired AnalysisService analysisService;
    @Test
    public void test() {
        try {
            System.out.println("AI INSIGHTS: " + analysisService.getAiInsights(1L, null, null));
            System.out.println("AI REPORT: " + analysisService.getAiReport(1L, null, null));
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}
