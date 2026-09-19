plugins {
    id("java")
    application
}

group = "com.hotel"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Gemini API JSON 직렬화/역직렬화용 Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")

    // 단위 테스트 (JUnit 5)
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.hotel.Main")
}

tasks.test {
    useJUnitPlatform()
}