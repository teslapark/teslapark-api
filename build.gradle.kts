import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.allopen) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.micronaut.application) apply false
    alias(libs.plugins.micronaut.library) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

group = "com.teslapark"
version = "0.1.0"

val ktlintVersion: String = libs.versions.ktlint.get()
val detektVersion: String = libs.versions.detekt.get()
val detektConfigFile = file("config/detekt/detekt.yml")
val unitTestBundle = libs.bundles.unitTest
val junitLauncher = libs.junit.platform.launcher

allprojects {
    group = rootProject.group
    version = rootProject.version
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "jacoco")

    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(21)
    }

    extensions.configure<KtlintExtension> {
        version.set(ktlintVersion)
        ignoreFailures.set(false)
        reporters {
            reporter(ReporterType.PLAIN)
        }
    }

    extensions.configure<DetektExtension> {
        toolVersion = detektVersion
        config.setFrom(detektConfigFile)
        buildUponDefaultConfig = true
        ignoreFailures = false
        parallel = true
        basePath = rootDir.absolutePath
    }

    extensions.configure<JacocoPluginExtension> {
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

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named<Test>("test"))
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    tasks.named("check") {
        dependsOn(tasks.named("jacocoTestReport"))
    }

    dependencies {
        add("testImplementation", unitTestBundle)
        add("testRuntimeOnly", junitLauncher)
    }
}
