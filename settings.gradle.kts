plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "spring-multi-module-template"

include("projectA")
include("ProjectB")
