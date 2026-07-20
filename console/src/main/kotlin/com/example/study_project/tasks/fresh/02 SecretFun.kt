package com.example.study_project.tasks.fresh

// понять что делает эта функция и и узнать ее

public inline fun <T, R> Iterable<T>.secretFunctionFirst(
    secretLambda: (T) -> R,
): List<R> {
    return secretFunctionSecond(
        ArrayList<R>(collectionSizeOrDefault(10)),
        secretLambda,
    )
}


public inline fun <T, R, C : MutableCollection<in R>>
        Iterable<T>.secretFunctionSecond(
    destination: C,
    secretLambda: (T) -> R,
): C {
    for (item in this) {
        destination.add(secretLambda(item))
    }

    return destination
}
