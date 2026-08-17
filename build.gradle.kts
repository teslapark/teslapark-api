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
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
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
    runtimeOnly(libs.swagger.ui)
    runtimeOnly(libs.flyway.mysql)
    runtimeOnly(libs.mysql.connector)
    runtimeOnly(libs.logback.jackson)
    runtimeOnly(libs.logback.json.classic)

    testImplementation(libs.bundles.unitTest)
    testImplementation(libs.konsist)
    testImplementation(libs.bundles.integrationTest)
    testImplementation(libs.flyway.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set("com.teslapark.ApplicationKt")
}

tasks.processResources {
    from(layout.projectDirectory.file("docs/api/openapi.yaml")) {
        into("META-INF/swagger/views")
    }
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

val executionDataFiles = fileTree(layout.buildDirectory).include("jacoco/*.exec")

val jacocoAggregatedReport =
    tasks.register<JacocoReport>("jacocoAggregatedReport") {
        description = "Merges unit and integration coverage into a single report"
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        dependsOn(tasks.test, integrationTestTask)
        executionData.setFrom(executionDataFiles)
        sourceSets(sourceSets.main.get())
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

val globalCoverageGate =
    tasks.register<JacocoCoverageVerification>("jacocoGlobalCoverageGate") {
        description = "Fails the build when global line coverage drops below the agreed floor"
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        dependsOn(jacocoAggregatedReport)
        executionData.setFrom(executionDataFiles)
        sourceSets(sourceSets.main.get())
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = "0.80".toBigDecimal()
                }
            }
        }
    }

val domainCoverageGate =
    tasks.register<JacocoCoverageVerification>("jacocoDomainCoverageGate") {
        description = "Fails the build when the domain drops below the agreed floor"
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        dependsOn(jacocoAggregatedReport)
        executionData.setFrom(executionDataFiles)
        sourceSets(sourceSets.main.get())
        classDirectories.setFrom(
            files(
                sourceSets.main
                    .get()
                    .output.classesDirs
                    .map { classes -> fileTree(classes) { include("com/teslapark/domain/**") } },
            ),
        )
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = "0.90".toBigDecimal()
                }
            }
        }
    }

tasks.check {
    dependsOn(tasks.jacocoTestReport, integrationTestTask, globalCoverageGate, domainCoverageGate)
}
