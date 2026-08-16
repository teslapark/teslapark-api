package com.teslapark.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import org.junit.jupiter.api.Test

class ArchitectureTest {
    private val production = Konsist.scopeFromProduction()

    @Test
    fun `the domain never imports a framework`() {
        production
            .files
            .withPackage(DOMAIN_PACKAGE)
            .assertFalse { file ->
                file.imports.any { import -> FORBIDDEN_IN_DOMAIN.any(import.name::startsWith) }
            }
    }

    @Test
    fun `the application never imports persistence`() {
        production
            .files
            .withPackage(APPLICATION_PACKAGE)
            .assertFalse { file ->
                file.imports.any { import -> import.name.startsWith(PERSISTENCE_PACKAGE) }
            }
    }

    @Test
    fun `the application never imports the infrastructure`() {
        production
            .files
            .withPackage(APPLICATION_PACKAGE)
            .assertFalse { file ->
                file.imports.any { import -> import.name.startsWith(INFRASTRUCTURE_ROOT) }
            }
    }

    @Test
    fun `the domain never imports the application or the infrastructure`() {
        production
            .files
            .withPackage(DOMAIN_PACKAGE)
            .assertFalse { file ->
                file.imports.any { import ->
                    import.name.startsWith(APPLICATION_ROOT) || import.name.startsWith(INFRASTRUCTURE_ROOT)
                }
            }
    }

    @Test
    fun `every output port is an interface`() {
        val ports = production.files.withPackage(PORT_PACKAGE)

        ports.flatMap { it.classes() }.shouldBeEmpty()
        ports.flatMap { it.objects() }.shouldBeEmpty()
        ports.flatMap { it.interfaces() }.size shouldBeGreaterThan 0

        ports.assertTrue { file -> file.interfaces().isNotEmpty() }
    }

    @Test
    fun `no production file carries a line or block comment`() {
        val offenders =
            production.files.filter { file -> containsComment(file.text) }.map { it.name }

        offenders.shouldBeEmpty()
    }

    @Test
    fun `persistence entities are never data classes`() {
        production
            .files
            .withPackage(ENTITY_PACKAGE)
            .flatMap { it.classes() }
            .assertFalse { entity -> entity.hasDataModifier }
    }

    private fun containsComment(source: String): Boolean {
        val withoutLiterals = stripLiterals(source)
        return withoutLiterals.contains(LINE_COMMENT) || withoutLiterals.contains(BLOCK_COMMENT)
    }

    private fun stripLiterals(source: String): String =
        source
            .replace(TRIPLE_QUOTED, "")
            .replace(SINGLE_QUOTED, "")

    private companion object {
        const val DOMAIN_PACKAGE = "com.teslapark.domain.."
        const val APPLICATION_PACKAGE = "com.teslapark.application.."
        const val PORT_PACKAGE = "com.teslapark.domain.port.."
        const val ENTITY_PACKAGE = "com.teslapark.infrastructure.persistence.entity.."
        const val APPLICATION_ROOT = "com.teslapark.application"
        const val INFRASTRUCTURE_ROOT = "com.teslapark.infrastructure"
        const val PERSISTENCE_PACKAGE = "jakarta.persistence"
        const val LINE_COMMENT = "//"
        const val BLOCK_COMMENT = "/*"

        val FORBIDDEN_IN_DOMAIN = listOf("io.micronaut", "jakarta.persistence", "com.fasterxml")
        val TRIPLE_QUOTED = Regex("\"\"\"[\\s\\S]*?\"\"\"")
        val SINGLE_QUOTED = Regex("\"(\\\\.|[^\"\\\\])*\"")
    }
}
