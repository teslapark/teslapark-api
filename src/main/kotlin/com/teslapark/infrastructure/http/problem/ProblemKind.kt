package com.teslapark.infrastructure.http.problem

import io.micronaut.http.HttpStatus

data class ProblemKind(
    val status: HttpStatus,
    val slug: String,
    val title: String,
)
