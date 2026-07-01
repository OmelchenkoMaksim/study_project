package com.example.study_project.yandex

import android.app.Activity
import android.os.Build
import java.io.Serializable

fun <T : Serializable> readSerializableExtra(
    sdkInt: Int,
    key: String,
    clazz: Class<T>,
    getTyped: (String, Class<T>) -> T?,
    getLegacy: (String) -> Serializable?,
): T? {
    return if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
        getTyped(key, clazz)
    } else {
        @Suppress("DEPRECATION")
        clazz.cast(getLegacy(key))
    }
}

inline fun <reified T : Serializable> Activity.serializableExtra(
    key: String,
): T? {
    return readSerializableExtra(
        sdkInt = Build.VERSION.SDK_INT,
        key = key,
        clazz = T::class.java,
        getTyped = { extraKey, extraClass -> intent.getSerializableExtra(extraKey, extraClass) },
        getLegacy = { extraKey -> intent.getSerializableExtra(extraKey) },
    )
}
// починить эту функцию и сделать расширением для активити

//fun <T : java.io.Serializable?> getSerializable(name: String) {
//    return intent.getSerializableExtra(name) as T
//}

data class UserArgs(
    val userId: Long,
    val userName: String,
) : Serializable

class ProfileActivity : Activity() {

    override fun onStart() {
        super.onStart()

        val args: UserArgs? = serializableExtra(ARG_USER)
    }

    private companion object {
        const val ARG_USER = "com.example.profile.ARG_USER"
    }
}

private class FakeIntent(
    private val extras: Map<String, Serializable>,
) {
    fun <T : Serializable> getSerializableExtra(name: String, clazz: Class<T>): T? =
        clazz.cast(extras[name])

    @Suppress("DEPRECATION")
    fun getSerializableExtra(name: String): Serializable? = extras[name]
}

private class FakeActivity(
    val fakeIntent: FakeIntent,
)

private inline fun <reified T : Serializable> FakeActivity.serializableExtra(key: String): T? =
    readSerializableExtra(
        sdkInt = Build.VERSION.SDK_INT,
        key = key,
        clazz = T::class.java,
        getTyped = { extraKey, extraClass -> fakeIntent.getSerializableExtra(extraKey, extraClass) },
        getLegacy = { extraKey -> fakeIntent.getSerializableExtra(extraKey) },
    )

fun main() {
    val argKey = "com.example.profile.ARG_USER"
    val user = UserArgs(userId = 42, userName = "Alice")

    // Как будто другой экран положил UserArgs в Intent перед открытием ProfileActivity.
    val intent = FakeIntent(mapOf(argKey to user))
    val activity = FakeActivity(intent)

    println("=== Демо serializableExtra ===")
    println("В Intent лежит: $user")
    println()

    val argsFromNewApi = readSerializableExtra(
        sdkInt = Build.VERSION_CODES.TIRAMISU, // API 33+ — getSerializableExtra(key, Class<T>)
        key = argKey,
        clazz = UserArgs::class.java,
        getTyped = { extraKey, extraClass -> intent.getSerializableExtra(extraKey, extraClass) },
        getLegacy = { extraKey -> intent.getSerializableExtra(extraKey) },
    )
    println("Новый API (>= 33): $argsFromNewApi")

    val argsFromLegacyApi = readSerializableExtra(
        sdkInt = Build.VERSION_CODES.S, // API 31 — getSerializableExtra(key) + cast
        key = argKey,
        clazz = UserArgs::class.java,
        getTyped = { extraKey, extraClass -> intent.getSerializableExtra(extraKey, extraClass) },
        getLegacy = { extraKey -> intent.getSerializableExtra(extraKey) },
    )
    println("Старый API (< 33): $argsFromLegacyApi")

    val argsLikeProfileActivity = activity.serializableExtra<UserArgs>(argKey)
    println("Через extension (как в ProfileActivity.onStart): $argsLikeProfileActivity")

    val missing = activity.serializableExtra<UserArgs>("unknown.key")
    println("Нет такого ключа → null: $missing")

    println()
    println("В реальном приложении то же самое делает ProfileActivity:")
    println("  val args: UserArgs? = serializableExtra(ARG_USER)")
}
