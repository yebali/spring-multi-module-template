/**
 * [Sharing catalogs](https://docs.gradle.org/current/userguide/platforms.html#sec:sharing-catalogs)
 */
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
