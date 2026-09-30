package com.hotel.aieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class EvalDataset {

    public static final String RESOURCE = "/ai-eval/tag-extraction-cases.json";

    private EvalDataset() {
    }

    public static List<EvalCase> load() {
        try (InputStream in = EvalDataset.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("평가 데이터셋을 찾을 수 없습니다: " + RESOURCE);
            }
            JsonNode root = new ObjectMapper().readTree(in);
            List<EvalCase> cases = new ArrayList<>();
            for (JsonNode node : root.path("cases")) {
                cases.add(new EvalCase(
                        node.path("id").asText(), node.path("lang").asText(), node.path("kind").asText(),
                        node.path("note").asText(),
                        strings(node.path("preferred")), strings(node.path("avoid")),
                        strings(node.path("optionalPreferred")), strings(node.path("optionalAvoid"))));
            }
            return List.copyOf(cases);
        } catch (IOException e) {
            throw new IllegalStateException("평가 데이터셋을 읽지 못했습니다.", e);
        }
    }

    private static Set<String> strings(JsonNode array) {
        Set<String> values = new LinkedHashSet<>();
        array.forEach(n -> values.add(n.asText()));
        return values;
    }
}
