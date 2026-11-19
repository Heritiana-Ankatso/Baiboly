// Pure Kotlin: Bible canon, reference parser, list logic, suggestions.
// No Android dependency, so another platform can reuse it.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
dependencies {
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}
