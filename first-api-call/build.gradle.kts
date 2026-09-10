plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    application
}

group = "com.eduai"
version = "0.1.0"

repositories {
    mavenCentral()
}

val ktorVersion = "3.5.2"
val koogVersion = "1.2.0"

dependencies {
    // Ktor server
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // Koog (JetBrains' AI framework) — DeepSeek interaction
    implementation("ai.koog:koog-agents:$koogVersion")
    implementation("ai.koog:prompt-executor-deepseek-client:$koogVersion-beta")

    // JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.6.3")

    // Tests
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation(kotlin("test-junit5"))
}

kotlin {
    jvmToolchain(23)
}

application {
    mainClass.set("com.eduai.ApplicationKt")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
