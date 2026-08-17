package com.teslapark.domain.model

enum class SessionStatus {
    ENTERED,
    PARKED,
    EXITED,
    CANCELLED,
    ;

    val isClosed: Boolean get() = this == EXITED || this == CANCELLED

    fun canTransitionTo(next: SessionStatus): Boolean =
        when (this) {
            ENTERED -> next == PARKED || next == EXITED || next == CANCELLED
            PARKED -> next == EXITED || next == CANCELLED
            EXITED, CANCELLED -> false
        }
}
