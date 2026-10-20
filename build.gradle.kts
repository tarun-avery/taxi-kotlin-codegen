plugins {
    application
    kotlin("jvm") version "2.4.21"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

group = "com.example.taxicodegen"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(25)
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
