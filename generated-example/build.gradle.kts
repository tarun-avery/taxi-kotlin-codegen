plugins {
    application
    kotlin("jvm") version "2.4.21"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

kotlin {
    jvmToolchain(25)
    sourceSets.main {
        kotlin.srcDir(layout.buildDirectory.dir("generated/sources/taxi/main/kotlin"))
        kotlin.srcDir(layout.buildDirectory.dir("generated/sources/evolution/main/kotlin"))
    }
}

ktlint {
    version.set("1.8.0")
    filter {
        exclude {
            it.file.toPath().startsWith(
                layout.buildDirectory
                    .get()
                    .asFile
                    .toPath(),
            )
        }
    }
}

dependencies {
    implementation("org.agrona:agrona:2.7.0")
    implementation("io.aeron:aeron-driver:1.53.3")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}

tasks.compileKotlin {
    dependsOn(rootProject.tasks.named("generatePojos"), rootProject.tasks.named("generateEvolution"))
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED")
}

application {
    mainClass.set("com.example.taxicodegen.AeronDemoKt")
    applicationDefaultJvmArgs = listOf("--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED")
}
