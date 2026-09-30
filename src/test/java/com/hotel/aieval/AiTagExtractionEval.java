package com.hotel.aieval;

import com.hotel.config.AiModelConfig;
import com.hotel.domain.TagPreference;
import com.hotel.service.AiPreferenceParser;
import com.hotel.util.EnvLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 실제 Gemini API로 태그 추출 정확도를 잰다.
 *
 * 돈이 들고 네트워크가 필요하며 결과가 실행마다 조금씩 달라서 일반 테스트({@code ./gradlew test})에는 포함되지 않는다.
 * 아래처럼 직접 실행한다.
 * <pre>
 *   ./gradlew aiEval
 * </pre>
 * GEMINI_API_KEY가 없으면 건너뛴다. 결과는 build/reports/ai-eval/report.md 와 results.json 에 남고 요약이 콘솔에도 찍힌다.
 * 기준 점수를 강제하고 싶으면 -Dai.eval.min-f1=0.8 처럼 넘긴다. 기본값은 점수를 재기만 하고 실패시키지 않는다.
 */
@Tag("ai-eval")
class AiTagExtractionEval {

    @Test
    @DisplayName("[AI 평가] 기본 태그 7개 기준 한국어, 일본어, 영어 메모의 태그 추출 정확도")
    void measureTagExtractionAccuracy() throws Exception {
        String apiKey = EnvLoader.get("GEMINI_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank(), "GEMINI_API_KEY가 없어 AI 평가를 건너뜁니다.");

        AiModelConfig config = AiModelConfig.fromEnvOrDefault();
        // 운영과 같은 파서를 쓰되, 태그 사전만 기본 태그 7개로 고정한다. 호텔마다 다른 커스텀 태그가 결과를 흔들지 않게 하기 위함이다.
        AiPreferenceParser parser = new AiPreferenceParser(DefaultTagCatalog.newRepository(), apiKey, config);
        List<EvalCase> cases = EvalDataset.load();

        Map<String, TagPreference> predictions = EvalRunner.predict(parser, cases, EvalRunner.DEFAULT_BATCH_SIZE);
        EvalMetrics.Summary summary = EvalMetrics.evaluate(cases, predictions);

        Path reportDir = Path.of("build", "reports", "ai-eval");
        EvalReport.write(reportDir, summary, config.getModelName(), config.getTemperature());

        System.out.println();
        System.out.println("==== AI 태그 추출 평가 결과 ====");
        System.out.printf("사례 %d건 / AI가 답한 건 %d건%n", summary.caseCount(), summary.answeredCount());
        System.out.printf("정밀도 %s  재현율 %s  F1 %s  완전 일치 %s%n",
                EvalReport.pct(summary.overall().precision()), EvalReport.pct(summary.overall().recall()),
                EvalReport.pct(summary.overall().f1()), EvalReport.pct(summary.exactMatchRate()));
        System.out.printf("무관 메모를 빈 결과로 처리 %s / 선호-기피 뒤집힘 %d / 사전에 없는 태그 %d%n",
                EvalReport.pct(summary.emptyCaseAccuracy()), summary.polarityFlipCount(), summary.unknownTagCount());
        System.out.println("리포트: " + reportDir.resolve("report.md").toAbsolutePath());

        assumeTrue(summary.answeredCount() > 0,
                "AI가 한 건도 답하지 않았습니다. API 키, 크레딧, 네트워크를 확인하세요. (서버 로그의 Gemini 오류 참고)");

        String minF1 = System.getProperty("ai.eval.min-f1");
        if (minF1 != null && !minF1.isBlank()) {
            double threshold = Double.parseDouble(minF1);
            assertTrue(summary.overall().f1() >= threshold,
                    String.format("F1 %.3f 이(가) 기준 %.3f 보다 낮습니다.", summary.overall().f1(), threshold));
        }
    }
}
