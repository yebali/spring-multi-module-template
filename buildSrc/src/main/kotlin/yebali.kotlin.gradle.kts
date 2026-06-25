import org.gradle.api.JavaVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jlleitschuh.gradle.ktlint")
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:$kotlinLoggingVersion")
}

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    version.set("1.8.0")
    additionalEditorconfig.set(
        mapOf(
            "ktlint_standard_max-line-length" to "disabled",
            "ktlint_standard_multiline-expression-wrapping" to "disabled",
            "ktlint_standard_chain-method-continuation" to "disabled",
            "ktlint_standard_class-signature" to "disabled",
            "ktlint_standard_function-signature" to "disabled",
            "ktlint_standard_property-naming" to "disabled",
            "ktlint_standard_if-else-wrapping" to "disabled",
            "ktlint_standard_assignment" to "disabled",
            "ktlint_standard_no-unused-imports" to "enabled",
            "ktlint_standard_no-wildcard-imports" to "enabled",
        ),
    )
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.set(listOf("-Xjsr305=strict"))
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.Embeddable")
    annotation("jakarta.persistence.MappedSuperclass")
}
