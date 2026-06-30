plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<JavaExec>().configureEach {
    // Keep console output readable across OSes (especially Windows terminals).
    systemProperty("file.encoding", "UTF-8")
}

tasks.register<JavaExec>("runTheDog") {
    group = "application"
    description = "Run TheDog main()"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.study_project.yandex._02_TheDogKt")
}
