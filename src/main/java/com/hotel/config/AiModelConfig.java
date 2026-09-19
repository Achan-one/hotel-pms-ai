package com.hotel.config;

import com.hotel.util.EnvLoader;

public class AiModelConfig {

    private static final String DEFAULT_MODEL = "gemini-2.5-flash";
    private static final double DEFAULT_TEMPERATURE = 0.1; // JSON 파싱/분류는 0.0~0.2가 최적
    private static final int DEFAULT_THINKING_BUDGET = 0;   // 0: 비활성화(초고속), 양수: 추론 토큰 예산

    private final String modelName;
    private final double temperature;
    private final int thinkingBudget;

    public AiModelConfig(String modelName, double temperature, int thinkingBudget) {
        this.modelName = modelName;
        this.temperature = temperature;
        this.thinkingBudget = thinkingBudget;
    }

    /**
     * .env 파일에서 설정을 우선 로드하고, 미지정 시 기본값을 적용합니다.
     */
    public static AiModelConfig fromEnvOrDefault() {
        String model = getEnvWithDefault("GEMINI_MODEL_NAME", DEFAULT_MODEL);
        double temp = parseDoubleOrDefault("GEMINI_TEMPERATURE", DEFAULT_TEMPERATURE);
        int budget = parseIntOrDefault("GEMINI_THINKING_BUDGET", DEFAULT_THINKING_BUDGET);

        return new AiModelConfig(model, temp, budget);
    }

    private static String getEnvWithDefault(String key, String defaultValue) {
        String value = EnvLoader.get(key);
        return (value != null && !value.isBlank()) ? value.trim() : defaultValue;
    }

    private static double parseDoubleOrDefault(String key, double defaultValue) {
        String value = EnvLoader.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            System.err.println("[AiModelConfig] " + key + " 파싱 실패. 기본값 적용: " + defaultValue);
            return defaultValue;
        }
    }

    private static int parseIntOrDefault(String key, int defaultValue) {
        String value = EnvLoader.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            System.err.println("[AiModelConfig] " + key + " 파싱 실패. 기본값 적용: " + defaultValue);
            return defaultValue;
        }
    }

    public String getModelName() {
        return modelName;
    }

    public double getTemperature() {
        return temperature;
    }

    public int getThinkingBudget() {
        return thinkingBudget;
    }

    @Override
    public String toString() {
        return String.format("[모델: %s | 온도: %.2f | 띵킹예산: %s]",
                modelName,
                temperature,
                thinkingBudget > 0 ? (thinkingBudget + " tokens") : "OFF(빠른응답)");
    }
}