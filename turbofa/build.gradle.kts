// build.gradle.kts
plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    application
}

group = "com.eduai"
version = "0.1.0"

repositories { mavenCentral() }

val ktorVersion = "3.5.2"
val koogVersion = "1.2.0"

dependencies {
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")

    // Koog (JetBrains' AI framework) — all DeepSeek interaction goes through it
    implementation("ai.koog:koog-agents:$koogVersion")
    implementation("ai.koog:prompt-executor-deepseek-client:$koogVersion-beta")
    implementation("ai.koog:http-client-ktor:$koogVersion")

    // External DB access: plain JDBC + HikariCP, read-only
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("ch.qos.logback:logback-classic:1.6.3")

    testImplementation(kotlin("test-junit5"))
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

kotlin { jvmToolchain(23) }

application { mainClass.set("com.eduai.turbofa.ApplicationKt") }

tasks.test { useJUnitPlatform() }
