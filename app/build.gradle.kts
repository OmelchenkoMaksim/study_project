// обратите внимание что плагины в build.gradle.kts уровня модуля (этот файл)
// дублируют плагины build.gradle.kts уровня проекта (не этот файл)
// это не ошибка - так как блок android { } нельзя будет настроить как и зависимости без указания этих плагинов тут
// т.е. плагины одни и те же, но на уровне модуля (тут) они получают дополнительную настройку
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
}

/*
- Аннотация @Parcelize: Плагин id("kotlin-parcelize")
        предоставляет поддержку аннотации @Parcelize. Эта аннотация позволяет автоматически
генерировать реализацию интерфейса Parcelable для классов Kotlin. Интерфейс Parcelable
        используется для передачи объектов между компонентами Android, такими как активности и
        фрагменты, и аннотация @Parcelize значительно упрощает этот процесс.
*/



android {
    namespace = "com.example.study_project"
    // compileSdk это версия сдк на которой вы пишете приложение
    // и например если у вас не скачена сдк 33 (SDK Manager)
    // то у вас не будет документации к классам Андроид типа Активити
    compileSdk = 33

    defaultConfig {
        applicationId = "com.example.study_project"
        // при попытке установить приложение на смартфон с версией Андроид ниже
        // уровня minSdk, например на 23 Api будет ошибка и оно не установится
        minSdk = 24
        // это версия Андроид под которую приложение разрабатывается,
        // но оно будет работать на всех версиях выше minSdk
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

// это подключение зависимостей для этого модуля (app)
// сюда мы подключаем все дополнительные библиотеки
// например для загрузки изображений - Glide,
// для инверсии зависимостей - Dagger,
// для современной многопоточности - Coroutines,
// а так же если бы у приложения было несколько модулей
// то зависимость для других модулей так же бы указывалась тут
dependencies {
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
}