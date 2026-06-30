plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

tasks.register<JavaExec>("runTheDog") {
    group = "application"
    description = "Run TheDog main()"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.study_project.yandex._02_TheDogKt")
}
