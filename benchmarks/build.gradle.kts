plugins {
    application
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

ktlint {
    version.set("1.8.0")
}

dependencies {
    implementation(project(":generated-example"))
    implementation("org.agrona:agrona:2.7.0")
    implementation("org.openjdk.jmh:jmh-core:1.37")
    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

application {
    mainClass.set("org.openjdk.jmh.Main")
    applicationDefaultJvmArgs = listOf("--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED")
}

tasks.named<JavaExec>("run") {
    workingDir(rootProject.projectDir)
    doFirst {
        layout.buildDirectory
            .dir("reports/jmh")
            .get()
            .asFile
            .mkdirs()
    }
}
