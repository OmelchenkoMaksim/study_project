import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// обратите внимание, что плагины в build.gradle.kts уровня модуля (этот файл)
// дублируют плагины build.gradle.kts уровня проекта (не этот файл)
// это не ошибка - так как блок android { } нельзя будет настроить как и зависимости без указания этих плагинов тут
// т.е. плагины одни и те же, но на уровне модуля (тут) они получают дополнительную настройку
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

/*
-  Плагин id("kotlin-parcelize")
        предоставляет поддержку аннотации @Parcelize. Эта аннотация - это продвинутая версия Serializable специально для Android,
        она позволяет автоматически генерировать реализацию интерфейса Parcelable для классов Kotlin. Интерфейс Parcelable
        используется для передачи объектов между компонентами Android, такими как активности и
        фрагменты, и аннотация @Parcelize значительно упрощает этот процесс (ищите пример в этом приложении).
*/

extensions.configure<ApplicationExtension> {
    namespace = "com.example.study_project"
    // compileSdk это версия сдк на которой вы пишете приложение
    // и например если у вас не скачена сдк 33 (SDK Manager)
    // то у вас не будет документации к классам Андроид типа Активити
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.study_project"
        // при попытке установить приложение на смартфон с версией Андроид ниже
        // уровня minSdk, например на 23 Api будет ошибка и оно не установится
        minSdk = 24
        // это версия Андроид под которую приложение разрабатывается,
        // но оно будет работать на всех версиях выше minSdk
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
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
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.14.0")
}