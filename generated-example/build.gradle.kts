plugins {
    kotlin("jvm") version "2.4.21"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

kotlin {
    jvmToolchain(25)
    sourceSets.main {
        kotlin.srcDir(layout.buildDirectory.dir("generated/sources/taxi/main/kotlin"))
    }
}

ktlint {
    version.set("1.8.0")
}

dependencies {
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}

tasks.compileKotlin {
    dependsOn(rootProject.tasks.named("generatePojos"))
}

tasks.test {
    useJUnitPlatform()
}
