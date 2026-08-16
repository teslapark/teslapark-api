package com.teslapark.infrastructure.health

import io.micronaut.core.async.publisher.Publishers
import io.micronaut.health.HealthStatus
import io.micronaut.management.health.indicator.HealthIndicator
import io.micronaut.management.health.indicator.HealthResult
import io.micronaut.management.health.indicator.annotation.Readiness
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import javax.sql.DataSource

@Readiness
@Singleton
class DatabaseHealthIndicator(
    private val dataSource: DataSource,
) : HealthIndicator {
    override fun getResult(): Publisher<HealthResult> {
        val probe =
            runCatching {
                dataSource.connection.use { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery(PROBE).use { rows -> rows.next() }
                    }
                }
            }

        val builder =
            if (probe.getOrDefault(false)) {
                HealthResult.builder(NAME, HealthStatus.UP)
            } else {
                HealthResult.builder(NAME, HealthStatus.DOWN).details(mapOf("error" to "database is unreachable"))
            }

        return Publishers.just(builder.build())
    }

    private companion object {
        const val NAME = "database"
        const val PROBE = "SELECT 1"
    }
}
