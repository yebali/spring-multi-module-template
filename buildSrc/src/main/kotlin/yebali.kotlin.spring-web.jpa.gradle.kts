import gradle.kotlin.dsl.accessors._f916be8616a61deeca485a4e11f794c0.testRuntimeOnly

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
    implementation("com.querydsl:querydsl-jpa:${querydslVersion}:jakarta")
    kapt("com.querydsl:querydsl-apt:${querydslVersion}:jakarta")

    testRuntimeOnly("com.h2database:h2")
}