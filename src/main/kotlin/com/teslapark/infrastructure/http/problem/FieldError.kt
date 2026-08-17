package com.teslapark.infrastructure.http.problem

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
class FieldError(
    @JsonProperty("field") val field: String,
    @JsonProperty("message") val message: String,
)
