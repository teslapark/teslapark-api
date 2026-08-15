plugins {
    alias(libs.plugins.ksp)
    alias(libs.plugins.micronaut.library)
}

micronaut {
    version(libs.versions.micronaut.get())
    processing {
        incremental(true)
        annotations("com.teslapark.*")
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(libs.micronaut.inject.kotlin)
}
