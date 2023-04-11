package com.example.study_project.yandex

import android.app.Activity

// починить эту функцию и сделать расширением для активити

inline fun <reified T : java.io.Serializable?> Activity.getSerializable(name: String) =
    intent.getSerializableExtra(name) as T



