plugins {
    id("yebali.kotlin.spring-web")
    kotlin("kapt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // querydsl
    implementation("io.github.openfeign.querydsl:querydsl-jpa:${querydslVersion}")
    kapt("io.github.openfeign.querydsl:querydsl-apt:${querydslVersion}")

    testRuntimeOnly("com.h2database:h2")
}