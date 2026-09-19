package com.hotel.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class EnvLoader {

    private static final String ENV_FILE_NAME = ".env";

    private EnvLoader() {
        // 유틸리티 클래스 인스턴스화 방지
    }

    /**
     * 지정한 키에 해당하는 환경 변수 값을 반환합니다.
     * 1순위: 프로젝트 루트의 .env 파일
     * 2순위: OS 환경 변수 (System.getenv)
     *
     * @param keyName 조회할 키 이름 (예: "GEMINI_API_KEY")
     * @return 키에 매핑된 값 또는 찾지 못했을 경우 null
     */
    public static String get(String keyName) {
        if (keyName == null || keyName.isBlank()) {
            return null;
        }

        String fileValue = loadFromDotEnv(keyName);
        if (fileValue != null && !fileValue.isBlank()) {
            return fileValue;
        }

        return System.getenv(keyName);
    }

    private static String loadFromDotEnv(String keyName) {
        File envFile = new File(ENV_FILE_NAME);
        if (!envFile.exists() || !envFile.isFile()) {
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(envFile, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // 빈 줄 또는 주석 무시
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int delimiterIndex = line.indexOf('=');
                if (delimiterIndex <= 0) {
                    continue;
                }

                String currentKey = line.substring(0, delimiterIndex).trim();
                if (currentKey.equals(keyName)) {
                    String value = line.substring(delimiterIndex + 1).trim();
                    // 따옴표로 감싸진 경우 따옴표 제거 ("value" -> value)
                    if ((value.startsWith("\"") && value.endsWith("\"")) ||
                            (value.startsWith("'") && value.endsWith("'"))) {
                        return value.substring(1, value.length() - 1);
                    }
                    return value;
                }
            }
        } catch (IOException e) {
            System.err.println("[EnvLoader] .env 파일 읽기 중 오류 발생: " + e.getMessage());
        }

        return null;
    }
}