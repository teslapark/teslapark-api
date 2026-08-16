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

val integrationTest: SourceSet =
    sourceSets.create("integrationTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }

configurations["integrationTestImplementation"].extendsFrom(configurations.testImplementation.get())
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    implementation(libs.micronaut.http.server.netty)
    implementation(libs.micronaut.jackson.databind)
    implementation(libs.micronaut.management)
    implementation(libs.micronaut.http.client)
    implementation(libs.micronaut.retry)
    implementation(libs.micronaut.micrometer.core)
    implementation(libs.micronaut.micrometer.registry.prometheus)
    implementation(libs.micronaut.flyway)
    implementation(libs.micronaut.jdbc.hikari)
    runtimeOnly(libs.snakeyaml)
    runtimeOnly(libs.flyway.mysql)
    runtimeOnly(libs.mysql.connector)
    runtimeOnly(libs.logback.jackson)
    runtimeOnly(libs.logback.json.classic)

    testImplementation(libs.bundles.unitTest)
    testImplementation(libs.bundles.integrationTest)
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

val integrationTestTask =
    tasks.register<Test>("integrationTest") {
        description = "Runs the Testcontainers backed integration suite"
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        testClassesDirs = integrationTest.output.classesDirs
        classpath = integrationTest.runtimeClasspath
        useJUnitPlatform()
        shouldRunAfter(tasks.test)
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

tasks.check {
    dependsOn(tasks.jacocoTestReport, integrationTestTask)
}
