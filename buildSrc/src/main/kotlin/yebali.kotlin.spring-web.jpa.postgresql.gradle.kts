plugins {
    id("yebali.kotlin.spring-web.jpa")
}

repositories {
    mavenCentral()
}

dependencies {
    runtimeOnly("org.postgresql:postgresql")
}