package com.hotel.aieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hotel.aieval.EvalMetrics.CaseResult;
import com.hotel.aieval.EvalMetrics.Counts;
import com.hotel.aieval.EvalMetrics.Summary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/** 평가 결과를 사람이 읽는 Markdown과 기계가 읽는 JSON으로 만든다. */
public final class EvalReport {

    private EvalReport() {
    }

    public static String markdown(Summary s, String modelName, double temperature, LocalDateTime at) {
        StringBuilder sb = new StringBuilder();
        sb.append("# AI 태그 추출 정확도 평가\n\n");
        sb.append("- 실행 시각: ").append(at.withNano(0)).append('\n');
        sb.append("- 모델: `").append(modelName).append("` (temperature ").append(temperature).append(")\n");
        sb.append("- 태그 사전: 시스템 기본 태그 7개 (").append(String.join(", ", DefaultTagCatalog.CODES.stream().sorted().toList())).append(")\n");
        sb.append("- 사례 수: ").append(s.caseCount()).append("건, 그중 AI가 답한 건 ").append(s.answeredCount()).append("건\n\n");

        sb.append("## 요약\n\n");
        sb.append("| 지표 | 값 |\n|---|---|\n");
        sb.append(row("정밀도 (Precision)", pct(s.overall().precision())));
        sb.append(row("재현율 (Recall)", pct(s.overall().recall())));
        sb.append(row("F1", pct(s.overall().f1())));
        sb.append(row("완전 일치율 (사례 단위)", pct(s.exactMatchRate()) + " (" + s.exactMatchCount() + "/" + s.caseCount() + ")"));
        sb.append(row("무관 메모를 빈 결과로 처리", pct(s.emptyCaseAccuracy()) + " (" + s.emptyCasesCorrect() + "/" + s.emptyCases() + ")"));
        sb.append(row("선호/기피를 뒤집은 횟수", String.valueOf(s.polarityFlipCount())));
        sb.append(row("사전에 없는 태그를 만든 횟수", String.valueOf(s.unknownTagCount())));
        sb.append("\n> 비교 단위는 (태그, 선호/기피) 쌍입니다. 정답에 없어도 허용(optional)으로 표시된 쌍은 예측해도 오탐으로 세지 않습니다.\n\n");

        sb.append("## 언어별\n\n| 언어 | 정밀도 | 재현율 | F1 |\n|---|---|---|---|\n");
        s.byLanguage().forEach((lang, c) -> sb.append(prf(lang, c)));

        sb.append("\n## 태그별\n\n| 태그 | 정밀도 | 재현율 | F1 | 맞힘 | 오탐 | 놓침 |\n|---|---|---|---|---|---|---|\n");
        s.byTag().forEach((tag, c) -> sb.append(String.format("| %s | %s | %s | %s | %d | %d | %d |\n",
                tag, pct(c.precision()), pct(c.recall()), pct(c.f1()), c.tp(), c.fp(), c.fn())));

        sb.append("\n## 유형별 완전 일치율\n\n| 유형 | 사례 수 | 완전 일치 |\n|---|---|---|\n");
        s.exactMatchByKind().forEach((kind, v) -> sb.append(String.format("| %s | %d | %s |\n",
                kind, (int) v[0], pct(v[1] / v[0]))));

        sb.append("\n## 틀린 사례\n\n");
        long wrong = s.results().stream().filter(r -> !r.exactMatch()).count();
        if (wrong == 0) {
            sb.append("없음\n");
        } else {
            sb.append("| ID | 메모 | 오탐 | 놓침 | 뒤집힘 |\n|---|---|---|---|---|\n");
            for (CaseResult r : s.results()) {
                if (r.exactMatch()) continue;
                sb.append(String.format("| %s | %s | %s | %s | %s |\n", r.evalCase().id(),
                        r.answered() ? escape(r.evalCase().note()) : escape(r.evalCase().note()) + " (AI 응답 없음)",
                        join(r.falsePositives()), join(r.falseNegatives()), join(r.polarityFlips())));
            }
        }
        sb.append("\n> AI 응답은 실행마다 조금씩 달라질 수 있습니다. 한 번의 결과로 단정하지 말고 여러 번 돌려 보세요.\n");
        return sb.toString();
    }

    public static void write(Path dir, Summary s, String modelName, double temperature) throws IOException {
        Files.createDirectories(dir);
        LocalDateTime now = LocalDateTime.now();
        Files.writeString(dir.resolve("report.md"), markdown(s, modelName, temperature, now), StandardCharsets.UTF_8);

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode json = mapper.createObjectNode();
        json.put("runAt", now.withNano(0).toString());
        json.put("model", modelName);
        json.put("temperature", temperature);
        json.put("cases", s.caseCount());
        json.put("answered", s.answeredCount());
        json.put("precision", round(s.overall().precision()));
        json.put("recall", round(s.overall().recall()));
        json.put("f1", round(s.overall().f1()));
        json.put("exactMatchRate", round(s.exactMatchRate()));
        json.put("emptyCaseAccuracy", round(s.emptyCaseAccuracy()));
        json.put("polarityFlips", s.polarityFlipCount());
        json.put("unknownTags", s.unknownTagCount());
        Files.writeString(dir.resolve("results.json"), mapper.writeValueAsString(json), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("predictions.json"), mapper.writeValueAsString(predictions(mapper, s)), StandardCharsets.UTF_8);
    }

    /** 사례마다 정답과 모델의 실제 예측을 나란히 남긴다. 점수를 믿기 전에 눈으로 확인하고 정답 라벨을 검토하는 데 쓴다. */
    private static com.fasterxml.jackson.databind.node.ArrayNode predictions(ObjectMapper mapper, Summary s) {
        var array = mapper.createArrayNode();
        for (CaseResult r : s.results()) {
            var node = array.addObject();
            node.put("id", r.evalCase().id());
            node.put("lang", r.evalCase().lang());
            node.put("kind", r.evalCase().kind());
            node.put("note", r.evalCase().note());
            node.put("answered", r.answered());
            node.put("exactMatch", r.exactMatch());
            var gold = node.putObject("gold");
            gold.set("preferred", mapper.valueToTree(r.evalCase().preferred().stream().sorted().toList()));
            gold.set("avoid", mapper.valueToTree(r.evalCase().avoid().stream().sorted().toList()));
            gold.set("optionalPreferred", mapper.valueToTree(r.evalCase().optionalPreferred().stream().sorted().toList()));
            gold.set("optionalAvoid", mapper.valueToTree(r.evalCase().optionalAvoid().stream().sorted().toList()));
            var predicted = node.putObject("predicted");
            predicted.set("preferred", mapper.valueToTree(r.predictedPreferred().stream().sorted().toList()));
            predicted.set("avoid", mapper.valueToTree(r.predictedAvoid().stream().sorted().toList()));
        }
        return array;
    }

    private static String prf(String label, Counts c) {
        return String.format("| %s | %s | %s | %s |\n", label, pct(c.precision()), pct(c.recall()), pct(c.f1()));
    }

    private static String row(String k, String v) {
        return "| " + k + " | " + v + " |\n";
    }

    static String pct(double v) {
        return String.format("%.1f%%", v * 100);
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private static String join(Iterable<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String i : items) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(i);
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    private static String escape(String text) {
        return text.replace("|", "\\|").replace("\n", " ");
    }
}
