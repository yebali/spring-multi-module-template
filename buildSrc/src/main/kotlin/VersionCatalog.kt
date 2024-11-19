import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

fun Project.version(key: String): String = extensions
    .getByType<VersionCatalogsExtension>()
    .named("libs")
    .findVersion(key)
    .get()
    .requiredVersion

val Project.kotlinxCoroutinesVersion
    get() = version("kotlinxCoroutines")

val Project.kotlinLoggingVersion
    get() = version("kotlinLogging")

val Project.springCloudVersion
    get() = version("springCloud")

val Project.querydslVersion
    get() = version("querydsl")


