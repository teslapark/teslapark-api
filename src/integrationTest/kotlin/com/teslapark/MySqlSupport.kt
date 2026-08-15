package com.teslapark

import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.DriverManager
import javax.sql.DataSource

object MySqlSupport {
    private const val HIKARI_POOL_SIZE = 40

    private val container: MySQLContainer<*> by lazy {
        MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("teslapark")
            .withUsername("root")
            .withPassword("teslapark")
            .withUrlParam("serverTimezone", "UTC")
            .withUrlParam("useSSL", "false")
            .withUrlParam("allowPublicKeyRetrieval", "true")
            .withCommand("--default-time-zone=+00:00")
            .also { it.start() }
    }

    fun createIsolatedDatabase(name: String): String {
        connectionTo(container.jdbcUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("DROP DATABASE IF EXISTS `$name`")
                statement.execute("CREATE DATABASE `$name` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci")
            }
        }
        return container.jdbcUrl.replace("/${container.databaseName}?", "/$name?")
    }

    fun connectionTo(jdbcUrl: String): Connection = DriverManager.getConnection(jdbcUrl, container.username, container.password)

    fun flywayFor(jdbcUrl: String): Flyway =
        Flyway
            .configure()
            .dataSource(jdbcUrl, container.username, container.password)
            .locations("classpath:db/migration")
            .load()

    fun dataSourceFor(jdbcUrl: String): DataSource =
        HikariDataSource().apply {
            this.jdbcUrl = jdbcUrl
            username = container.username
            password = container.password
            maximumPoolSize = HIKARI_POOL_SIZE
        }

    fun datasourceProperties(jdbcUrl: String): Map<String, Any> =
        mapOf(
            "datasources.default.url" to jdbcUrl,
            "datasources.default.username" to container.username,
            "datasources.default.password" to container.password,
            "datasources.default.driver-class-name" to "com.mysql.cj.jdbc.Driver",
            "datasources.default.dialect" to "MYSQL",
        )
}
