plugins {
    application
    kotlin("jvm") version "2.2.21"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

group = "com.example.taxicodegen"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(25)
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
}

java {
    sourceCompatibility = JavaVersion.VERSION_24
    targetCompatibility = JavaVersion.VERSION_24
}

ktlint {
    version.set("1.8.0")
}

tasks.named("check") {
    dependsOn("ktlintCheck")
}

application {
    mainClass.set("com.example.taxicodegen.MainKt")
}

dependencies {
    implementation("org.taxilang:compiler:1.73.0") {
        exclude(group = "ch.qos.logback", module = "logback-classic")
        // This CLI compiles local source strings; remote package resolution is not used.
        exclude(group = "org.taxilang", module = "package-manager")
    }
    constraints {
        implementation("org.apache.commons:commons-lang3:3.18.0") {
            because("Fix CVE-2025-48924 in Taxi’s transitive dependency")
        }
    }
    runtimeOnly("org.slf4j:slf4j-nop:2.0.17")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}

tasks.test {
    useJUnitPlatform()
}

val generatedSources = project(":generated-example").layout.buildDirectory.dir("generated/sources/taxi/main/kotlin")

val generatePojos by tasks.registering(JavaExec::class) {
    group = "code generation"
    description = "Generate Kotlin POJOs for the example Taxi schema."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.example.taxicodegen.MainKt")
    inputs.file("examples/order.taxi")
    outputs.dir(generatedSources)
    args("examples/order.taxi", "--codecs", generatedSources.get().asFile.absolutePath)
    doFirst {
        delete(generatedSources)
    }
}

tasks.named("check") {
    dependsOn(":generated-example:check")
}

val evolutionSources = project(":generated-example").layout.buildDirectory.dir("generated/sources/evolution/main/kotlin")
val generateEvolution by tasks.registering(JavaExec::class) {
    group = "code generation"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.example.taxicodegen.MainKt")
    inputs.file("examples/order-v1.taxi")
    outputs.dir(evolutionSources)
    args("examples/order-v1.taxi", "--codecs", evolutionSources.get().asFile.absolutePath)
    doFirst { delete(evolutionSources) }
}

val richSources = project(":generated-example").layout.buildDirectory.dir("generated/sources/rich/main/kotlin")
val generateRich by tasks.registering(JavaExec::class) {
    group = "code generation"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.example.taxicodegen.MainKt")
    inputs.file("examples/trade.taxi")
    outputs.dir(richSources)
    args("examples/trade.taxi", "--codecs", richSources.get().asFile.absolutePath)
    doFirst { delete(richSources) }
}
