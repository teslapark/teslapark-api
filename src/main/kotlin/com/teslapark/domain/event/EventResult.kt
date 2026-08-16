package com.teslapark.domain.event

enum class EventResult {
    PROCESSED,
    DUPLICATE,
    IGNORED,
    REJECTED,
}
