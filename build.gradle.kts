import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
    alias(libs.plugins.micronaut.application)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    jacoco
}

group = "com.teslapark"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

micronaut {
    version(libs.versions.micronaut.get())
    runtime("netty")
    processing {
        incremental(true)
        annotations("com.teslapark.*")
    }
}

dependencies {
    implementation(libs.micronaut.http.server.netty)
    implementation(libs.micronaut.jackson.databind)
    implementation(libs.micronaut.management)
    implementation(libs.micronaut.flyway)
    implementation(libs.micronaut.jdbc.hikari)
    runtimeOnly(libs.snakeyaml)
    runtimeOnly(libs.flyway.mysql)
    runtimeOnly(libs.mysql.connector)

    testImplementation(libs.bundles.unitTest)
    testImplementation(libs.bundles.integrationTest)
    testImplementation(libs.micronaut.http.client)
    testImplementation(libs.flyway.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set("com.teslapark.ApplicationKt")
}

ktlint {
    version.set(libs.versions.ktlint.get())
    ignoreFailures.set(false)
    reporters {
        reporter(ReporterType.PLAIN)
    }
}

detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom(file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    ignoreFailures = false
    parallel = true
    basePath = rootDir.absolutePath
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "21"
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(false)
        sarif.required.set(false)
        md.required.set(false)
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestReport)
}
