package com.teslapark.errors

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Controller("/test/unexpected-failure")
class ExplodingController {
    @Get
    fun explode(): String =
        throw IllegalStateException(
            "SQLSyntaxErrorException: Table 'teslapark.parking_session' doesn't exist at com.mysql.cj.jdbc",
        )
}
