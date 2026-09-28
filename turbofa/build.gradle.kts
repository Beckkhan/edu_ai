// build.gradle.kts
plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    application
    id("org.jlleitschuh.gradle.ktlint") version "12.2.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
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
    implementation("org.kodein.di:kodein-di-jvm:7.20.2")
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("ch.qos.logback:logback-classic:1.6.3")

    testImplementation(kotlin("test-junit5"))
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

kotlin { jvmToolchain(23) }

application { mainClass.set("com.eduai.turbofa.ApplicationKt") }

tasks.test { useJUnitPlatform() }

// Style gates (T19): ktlint reads .editorconfig; detekt builds on its default config
// baseline: pre-T19 findings recorded, so new diffs fail on NEW issues only
detekt {
    buildUponDefaultConfig = true
    baseline = file("detekt-baseline.xml")
}

// detekt 1.23.x supports jvm-target up to 22; the project toolchain is 23
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "22"
}
tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
    jvmTarget = "22"
}

// T19: pre-existing application code violates ktlint and must stay untouched, so the
// gate runs explicitly (code-style-reviewer) instead of failing every ./gradlew build;
// remove this exclusion when the follow-up task makes the tree ktlint-clean
tasks.named("check") {
    setDependsOn(dependsOn.filterNot {
        val name = when (it) {
            is Task -> it.name
            is TaskProvider<*> -> it.name
            else -> it.toString()
        }
        name.startsWith("ktlint")
    })
}
