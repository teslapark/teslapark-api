package com.teslapark.infrastructure.http.problem

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
class ProblemDetail(
    @JsonProperty("type") val type: String,
    @JsonProperty("title") val title: String,
    @JsonProperty("status") val status: Int,
    @JsonProperty("detail") val detail: String,
    @JsonProperty("instance") val instance: String,
    @JsonProperty("timestamp") val timestamp: String,
    @JsonProperty("requestId") val requestId: String?,
    @JsonProperty("errors") val errors: List<FieldError> = emptyList(),
)
