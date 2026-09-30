plugins {
    java
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.hotel"
version = "1.0-SNAPSHOT"

// Boot 3.3.4가 관리하는 Testcontainers 1.19.x는 최신 Docker Desktop 엔진에 붙지 못한다(400 응답).
// 통합 테스트가 돌아가도록 버전만 올린다.
extra["testcontainers.version"] = "1.21.4"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// 더 새 JDK(로컬은 25)로 빌드해도 Java 21에 없는 API를 쓰면 컴파일이 실패하게 한다. CI(JDK 21)와 결과가 같아진다.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

repositories {
    mavenCentral()
}

dependencies {
    // 1. Spring Boot Web & Validation
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // 2. Spring Security & JWT
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // 3. DB (Spring Data JPA & MySQL)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("com.mysql:mysql-connector-j")

    // 4. DB 형상 관리 (Flyway Core & MySQL 모듈)
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")

    // 5. Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // 6. 테스트 (JUnit 5 + Spring Boot Test + Security Test + Test H2)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("com.h2database:h2")

    // 7. 실제 MySQL(InnoDB)로 마이그레이션과 락 동작을 확인하는 통합 테스트 (Docker 필요)
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")
}

tasks.withType<Test> {
    // AI 정확도 평가(@Tag("ai-eval"))는 실제 Gemini API를 호출해 돈이 들므로 기본 테스트에서 뺀다. ./gradlew aiEval 로 따로 돌린다.
    useJUnitPlatform {
        if (name != "aiEval") excludeTags("ai-eval")
    }

    // Docker Desktop(macOS)은 소켓이 /var/run/docker.sock이 아니라 홈 디렉터리 아래에 있다.
    // Testcontainers가 못 찾으면 통합 테스트가 조용히 건너뛰어지므로 위치를 알려준다.
    val desktopSocket = file("${System.getProperty("user.home")}/.docker/run/docker.sock")
    if (System.getenv("DOCKER_HOST") == null && desktopSocket.exists()) {
        environment("DOCKER_HOST", "unix://${desktopSocket.absolutePath}")
        environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
    }
}

// 실제 Gemini API로 태그 추출 정확도를 재는 평가. GEMINI_API_KEY가 없으면 건너뛴다.
// 결과: build/reports/ai-eval/report.md, results.json  (기준 점수를 강제하려면 -Dai.eval.min-f1=0.8)
tasks.register<Test>("aiEval") {
    description = "실제 Gemini API로 AI 태그 추출 정확도를 평가한다 (비용 발생)"
    group = "verification"
    val testSourceSet = sourceSets["test"]
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    useJUnitPlatform {
        includeTags("ai-eval")
    }
    // 항상 새로 돌린다. 캐시된 결과를 재사용하지 않는다.
    outputs.upToDateWhen { false }
    systemProperty("ai.eval.min-f1", System.getProperty("ai.eval.min-f1") ?: "")
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
}
