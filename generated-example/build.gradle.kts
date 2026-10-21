plugins {
    application
    kotlin("jvm") version "2.2.21"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

kotlin {
    jvmToolchain(25)
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
    sourceSets.main {
        kotlin.srcDir(rootProject.tasks.named("generatePojos"))
        kotlin.srcDir(rootProject.tasks.named("generateEvolution"))
        kotlin.srcDir(rootProject.tasks.named("generateRich"))
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_24
    targetCompatibility = JavaVersion.VERSION_24
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
    dependsOn(
        rootProject.tasks.named("generatePojos"),
        rootProject.tasks.named("generateEvolution"),
        rootProject.tasks.named("generateRich"),
    )
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED")
}

application {
    mainClass.set("com.example.taxicodegen.AeronDemoKt")
    applicationDefaultJvmArgs = listOf("--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED")
}

val referenceTool by configurations.creating

dependencies {
    referenceTool("uk.co.real-logic:sbe-tool:1.41.0")
}

val referenceSources = layout.buildDirectory.dir("generated/reference")
val generateReference by tasks.registering(JavaExec::class) {
    description = "Generate independent SBE test-oracle codecs; never used by production generation."
    classpath = referenceTool
    mainClass.set("uk.co.real_logic.sbe.SbeTool")
    inputs.file("src/test/resources/reference.xml")
    outputs.dir(referenceSources)
    args(file("src/test/resources/reference.xml").absolutePath)
    systemProperty("sbe.output.dir", referenceSources.get().asFile.absolutePath)
    doFirst { delete(referenceSources) }
}

sourceSets.test {
    java.srcDir(generateReference)
}
tasks.compileTestKotlin { dependsOn(generateReference) }
tasks.compileTestJava { dependsOn(generateReference) }
