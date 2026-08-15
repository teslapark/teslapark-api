plugins {
    alias(libs.plugins.ksp)
    alias(libs.plugins.micronaut.application)
}

micronaut {
    version(libs.versions.micronaut.get())
    runtime("netty")
    processing {
        incremental(true)
        annotations("com.teslapark.*")
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":infrastructure"))

    implementation(libs.micronaut.http.server.netty)
    implementation(libs.micronaut.jackson.databind)
    implementation(libs.micronaut.management)
    runtimeOnly(libs.snakeyaml)

    testImplementation(libs.micronaut.http.client)
}

application {
    mainClass.set("com.teslapark.ApplicationKt")
}
