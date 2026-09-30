package com.hotel.aieval;

import com.hotel.domain.TagPreference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 예측 결과를 정답과 비교해 점수를 계산한다.
 *
 * 비교 단위는 (태그, 선호/기피) 쌍이다.
 *  - 맞힘(TP): 정답에 있는 쌍을 예측함
 *  - 오탐(FP): 정답에도, 허용(optional) 목록에도 없는 쌍을 예측함
 *  - 놓침(FN): 정답에 있는데 예측하지 못함
 * 허용 목록의 쌍은 예측해도 오탐으로 세지 않고, 예측하지 않아도 놓침으로 세지 않는다.
 */
public final class EvalMetrics {

    public record Counts(int tp, int fp, int fn) {
        public double precision() { return tp + fp == 0 ? 1.0 : (double) tp / (tp + fp); }
        public double recall() { return tp + fn == 0 ? 1.0 : (double) tp / (tp + fn); }
        public double f1() {
            double p = precision(), r = recall();
            return p + r == 0 ? 0.0 : 2 * p * r / (p + r);
        }
        Counts plus(Counts o) { return new Counts(tp + o.tp, fp + o.fp, fn + o.fn); }
    }

    public record CaseResult(EvalCase evalCase, Set<String> predictedPreferred, Set<String> predictedAvoid,
                             boolean answered, List<String> falsePositives, List<String> falseNegatives,
                             List<String> polarityFlips, List<String> unknownTags) {
        public boolean exactMatch() {
            return falsePositives.isEmpty() && falseNegatives.isEmpty();
        }
    }

    public record Summary(int caseCount, int answeredCount, int exactMatchCount,
                          Counts overall, Map<String, Counts> byTag, Map<String, Counts> byLanguage,
                          Map<String, double[]> exactMatchByKind, int emptyCases, int emptyCasesCorrect,
                          int polarityFlipCount, int unknownTagCount, List<CaseResult> results) {
        public double exactMatchRate() { return caseCount == 0 ? 0 : (double) exactMatchCount / caseCount; }
        public double emptyCaseAccuracy() { return emptyCases == 0 ? 1.0 : (double) emptyCasesCorrect / emptyCases; }
    }

    private static final String PREFER = "선호";
    private static final String AVOID = "기피";

    private EvalMetrics() {
    }

    /**
     * @param predictions 사례 ID -> 모델 예측. AI가 그 사례에 답하지 않았으면 키가 없다(빈 예측으로 채점하되 answered=false).
     */
    public static Summary evaluate(List<EvalCase> cases, Map<String, TagPreference> predictions) {
        List<CaseResult> results = new ArrayList<>();
        Counts overall = new Counts(0, 0, 0);
        Map<String, Counts> byTag = new TreeMap<>();
        Map<String, Counts> byLanguage = new TreeMap<>();
        Map<String, int[]> kindStats = new LinkedHashMap<>();
        int emptyCases = 0, emptyCorrect = 0, flips = 0, unknown = 0, answeredCount = 0, exact = 0;

        for (EvalCase c : cases) {
            TagPreference prediction = predictions.get(c.id());
            boolean answered = prediction != null;
            if (answered) answeredCount++;
            Set<String> predPref = answered ? prediction.preferredTags() : Set.of();
            Set<String> predAvoid = answered ? prediction.avoidTags() : Set.of();

            List<String> fp = new ArrayList<>(), fn = new ArrayList<>(), flipList = new ArrayList<>(), unknownList = new ArrayList<>();
            Counts caseCounts = new Counts(0, 0, 0);

            caseCounts = caseCounts.plus(compare(PREFER, c.preferred(), c.optionalPreferred(), predPref,
                    c.avoid(), fp, fn, flipList, byTag));
            caseCounts = caseCounts.plus(compare(AVOID, c.avoid(), c.optionalAvoid(), predAvoid,
                    c.preferred(), fp, fn, flipList, byTag));

            for (String tag : union(predPref, predAvoid)) {
                if (!DefaultTagCatalog.CODES.contains(tag)) unknownList.add(tag);
            }

            CaseResult result = new CaseResult(c, predPref, predAvoid, answered, fp, fn, flipList, unknownList);
            results.add(result);
            overall = overall.plus(caseCounts);
            byLanguage.merge(c.lang(), caseCounts, Counts::plus);
            flips += flipList.size();
            unknown += unknownList.size();
            if (result.exactMatch()) exact++;

            int[] stat = kindStats.computeIfAbsent(c.kind(), k -> new int[2]);
            stat[0]++;
            if (result.exactMatch()) stat[1]++;

            // 정답이 비어 있는 사례는 "없는 요구를 지어내지 않았는가"를 본다. 허용(optional) 태그는 감점하지 않는 것이
            // 이 채점의 원칙이므로, 오탐이 하나도 없으면 맞은 것으로 센다. (허용 태그를 답했다고 틀린 것으로 세면 원칙과 어긋난다)
            if (c.expectsNothing()) {
                emptyCases++;
                if (fp.isEmpty()) emptyCorrect++;
            }
        }

        Map<String, double[]> exactByKind = new LinkedHashMap<>();
        kindStats.forEach((kind, stat) -> exactByKind.put(kind, new double[]{stat[0], stat[1]}));
        return new Summary(cases.size(), answeredCount, exact, overall, byTag, byLanguage, exactByKind,
                emptyCases, emptyCorrect, flips, unknown, List.copyOf(results));
    }

    private static Counts compare(String polarity, Set<String> gold, Set<String> optional, Set<String> predicted,
                                  Set<String> oppositeGold, List<String> fp, List<String> fn, List<String> flips,
                                  Map<String, Counts> byTag) {
        int tp = 0, falsePos = 0, falseNeg = 0;
        for (String tag : gold) {
            if (predicted.contains(tag)) {
                tp++;
                byTag.merge(tag, new Counts(1, 0, 0), Counts::plus);
            } else {
                falseNeg++;
                fn.add(polarity + ":" + tag);
                byTag.merge(tag, new Counts(0, 0, 1), Counts::plus);
            }
        }
        for (String tag : predicted) {
            if (gold.contains(tag) || optional.contains(tag)) continue;
            falsePos++;
            fp.add(polarity + ":" + tag);
            byTag.merge(tag, new Counts(0, 1, 0), Counts::plus);
            if (oppositeGold.contains(tag)) flips.add(tag + " (정답은 " + (polarity.equals(PREFER) ? AVOID : PREFER) + ")");
        }
        return new Counts(tp, falsePos, falseNeg);
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> u = new LinkedHashSet<>(a);
        u.addAll(b);
        return u;
    }
}
