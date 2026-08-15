package com.teslapark.infrastructure.persistence.repository

import com.teslapark.domain.error.DomainError
import com.teslapark.domain.error.DomainResult
import com.teslapark.domain.error.asFailure
import jakarta.inject.Singleton
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLIntegrityConstraintViolationException
import javax.sql.DataSource

@Singleton
class JdbcOperations(
    private val dataSource: DataSource,
) {
    private val boundConnection = ThreadLocal<Connection?>()

    fun <T> inTransaction(block: (Connection) -> T): T {
        boundConnection.get()?.let { return block(it) }

        dataSource.connection.use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            boundConnection.set(connection)
            try {
                val result = block(connection)
                connection.commit()
                return result
            } catch (failure: Exception) {
                connection.rollback()
                throw failure
            } finally {
                boundConnection.remove()
                connection.autoCommit = previousAutoCommit
            }
        }
    }

    fun <T> readOnly(block: (Connection) -> T): T {
        boundConnection.get()?.let { return block(it) }
        return dataSource.connection.use(block)
    }
}

fun <T> Connection.query(
    sql: String,
    vararg parameters: Any?,
    read: (ResultSet) -> T,
): List<T> =
    prepareStatement(sql).use { statement ->
        statement.bind(parameters)
        statement.executeQuery().use { rows ->
            buildList { while (rows.next()) add(read(rows)) }
        }
    }

fun <T> Connection.queryFirst(
    sql: String,
    vararg parameters: Any?,
    read: (ResultSet) -> T,
): T? = query(sql, *parameters, read = read).firstOrNull()

fun Connection.update(
    sql: String,
    vararg parameters: Any?,
): Int =
    prepareStatement(sql).use { statement ->
        statement.bind(parameters)
        statement.executeUpdate()
    }

fun Connection.insertReturningId(
    sql: String,
    vararg parameters: Any?,
): Long =
    prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS).use { statement ->
        statement.bind(parameters)
        statement.executeUpdate()
        statement.generatedKeys.use { keys -> if (keys.next()) keys.getLong(1) else 0L }
    }

fun <T> translateConstraintViolation(
    block: () -> DomainResult<T>,
    onViolation: (String) -> DomainError,
): DomainResult<T> =
    try {
        block()
    } catch (violation: SQLIntegrityConstraintViolationException) {
        onViolation(violation.message.orEmpty()).asFailure()
    }

private fun PreparedStatement.bind(parameters: Array<out Any?>) {
    parameters.forEachIndexed { index, parameter -> setObject(index + 1, parameter) }
}
