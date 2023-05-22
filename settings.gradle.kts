pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// это имя проекта, оно не связано с именем приложения,
// да и в целом не является необходимым
// как я понимаю оно больше нужно для CI (это то чем девопсы занимаются)
rootProject.name = "Study Project"
// тут указано какие модули включены в приложение, у нас только один app
include(":app")

/*
Файл settings.gradle.kts - это файл настройки проекта в Kotlin DSL для системы сборки Gradle.
Он используется для настройки и управления зависимостями и репозиториями, используемыми в проекте.

В нашем примере файл содержит два блока: pluginManagement и dependencyResolutionManagement,
которые определяют репозитории, из которых будут загружаться плагины и зависимости проекта.

    Блок pluginManagement определяет репозитории, в которых будут искаться плагины Gradle.
    В данном случае, указаны следующие репозитории:
        google(): Репозиторий Google, который содержит плагины Gradle, разработанные или предоставленные Google.
        mavenCentral(): Репозиторий Maven Central, который является публичным репозиторием,
        содержащим множество популярных библиотек и плагинов.
        gradlePluginPortal(): Репозиторий Gradle Plugin Portal, который является официальным
        репозиторием плагинов Gradle и содержит множество плагинов, разработанных сообществом.

    Эти репозитории используются для поиска и загрузки плагинов,
    указанных в файле build.gradle.kts или других файлах настройки проекта.

    Блок dependencyResolutionManagement определяет репозитории, в которых будут искаться зависимости проекта.
    В данном случае, указаны следующие репозитории:
        google(): Репозиторий Google, который содержит зависимости, разработанные или предоставленные Google.
        mavenCentral(): Репозиторий Maven Central, который содержит множество популярных библиотек и зависимостей.

    Эти репозитории используются для загрузки зависимостей, указанных в файле build.gradle.kts
    или других файлах настройки проекта.

Общий результат этих блоков в settings.gradle.kts состоит в том, что
они определяют доступные репозитории для поиска плагинов и зависимостей проекта,
обеспечивая возможность загрузки необходимых ресурсов для сборки и
выполнения проекта с помощью системы сборки Gradle.
*/