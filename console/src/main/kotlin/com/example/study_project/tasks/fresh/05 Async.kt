package com.example.study_project.tasks.fresh

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        launch(Dispatchers.IO) {
            println(compute())
        }
    }
}

suspend fun compute(): String = coroutineScope {
    val color = async {
        delay(1_000)
        "purple"
    }

    val car = async<String> {
        delay(100)
        throw IOException()
    }

    color.await() + try {
        car.await()
    } catch (error: Exception) {
        "lamborghini"
    }
}

/*

это задача не только на «что выведется», а сразу на несколько тем:

async/await
→ structured concurrency
→ propagation исключений
→ отмена sibling-корутины
→ coroutineScope vs supervisorScope
→ порядок вычисления выражений

ЗАДАЧА ОРИГИНАЛ:

lifecycleScope.launch(Dispatchers.IO) {
    println(compute())
}

suspend fun compute(): String = coroutineScope {
    val color = async {
        delay(1_000)
        "purple"
    }

    val car = async<String> {
        delay(100)
        throw IOException()
    }

    color.await() + try {
        car.await()
    } catch (e: Exception) {
        "lamborghini"
    }
}

Что выведется

Ничего не выведется.

Примерно через 100 мс:

car выбросит IOException
→ coroutineScope будет отменён
→ color будет отменён
→ compute() завершится с IOException
→ println() не будет вызван
*/





/*


БРИФ ОТВЕТА:



Да, это задача не только на «что выведется», а сразу на несколько тем:

```text
async/await
→ structured concurrency
→ propagation исключений
→ отмена sibling-корутины
→ coroutineScope vs supervisorScope
→ порядок вычисления выражений
```

## 1. Код со скриншота

```kotlin
lifecycleScope.launch(Dispatchers.IO) {
    println(compute())
}

suspend fun compute(): String = coroutineScope {
    val color = async {
        delay(1_000)
        "purple"
    }

    val car = async<String> {
        delay(100)
        throw IOException()
    }

    color.await() + try {
        car.await()
    } catch (e: Exception) {
        "lamborghini"
    }
}
```

## 2. Что выведется

**Ничего не выведется.**

Примерно через 100 мс:

```text
car выбросит IOException
→ coroutineScope будет отменён
→ color будет отменён
→ compute() завершится с IOException
→ println() не будет вызван
```

На Android необработанное исключение из внешнего `launch` будет передано стандартному обработчику исключений и обычно приведёт к падению приложения или как минимум к stack trace в Logcat.

То есть результатом **не будет**:

```text
purplelamborghini
```

`compute()` вообще не вернёт `String`.

---

# 3. Пошаговое выполнение

## Шаг 1. Запускается внешняя корутина

```kotlin
lifecycleScope.launch(Dispatchers.IO) {
    println(compute())
}
```

Она запускается на `Dispatchers.IO`.

Перед тем как вызвать `println`, Kotlin должен сначала вычислить его аргумент:

```kotlin
compute()
```

Это концептуально выглядит так:

```kotlin
val result = compute()
println(result)
```

Если `compute()` выбросит исключение, до `println()` выполнение не дойдёт.

---

## Шаг 2. Создаётся обычный `coroutineScope`

```kotlin
suspend fun compute(): String = coroutineScope {
```

`coroutineScope` создаёт дочерний scope с обычной семантикой:

```text
ошибка одного child
→ отменяет scope
→ отменяет остальных children
→ исключение передаётся вызывающему коду
```

Это fail-fast-поведение structured concurrency. Официальная документация прямо указывает: если блок или любой дочерний coroutine падает, `coroutineScope` отменяет остальных детей и пробрасывает исходную ошибку вызывающей стороне. ([Kotlin][1])

---

## Шаг 3. Запускается `color`

```kotlin
val color = async {
    delay(1_000)
    "purple"
}
```

Тип переменной:

```kotlin
val color: Deferred<String>
```

`async` по умолчанию запускается сразу, а не при первом `await()`.

Внутри начинается:

```kotlin
delay(1_000)
```

Корутина приостанавливается примерно на одну секунду.

---

## Шаг 4. Запускается `car`

```kotlin
val car = async<String> {
    delay(100)
    throw IOException()
}
```

Тип:

```kotlin
val car: Deferred<String>
```

Через 100 мс корутина выбросит:

```kotlin
IOException()
```

### Почему код компилируется, если здесь нет `String`

Потому что выражение:

```kotlin
throw IOException()
```

имеет тип:

```kotlin
Nothing
```

`Nothing` является подтипом любого Kotlin-типа. Он означает: «это выражение никогда нормально не вернёт значение». Поэтому его можно использовать там, где ожидается `String`. ([Kotlin][2])

---

## Шаг 5. Основной блок ждёт `color`

```kotlin
color.await() + try {
    car.await()
} catch (e: Exception) {
    "lamborghini"
}
```

Операнды `+` вычисляются последовательно. Сначала вычисляется левая часть:

```kotlin
color.await()
```

И только после её успешного завершения могла бы вычислиться правая часть:

```kotlin
try {
    car.await()
} catch (...) {
    ...
}
```

Операторы вычисляют аргументы в порядке, соответствующем вызову функции оператора; для обычного `+` сначала вычисляется receiver — левая часть. ([Kotlin][3])

На этом этапе код ожидает `color`, который должен завершиться через одну секунду.

---

## Шаг 6. Через 100 мс падает `car`

```kotlin
throw IOException()
```

Но `car` является ребёнком обычного `coroutineScope`.

Поэтому происходит:

```text
car падает с IOException
        ↓
coroutineScope становится failed/cancelled
        ↓
color отменяется
        ↓
основной блок coroutineScope отменяется
```

`async` не просто сохраняет исключение внутри `Deferred`: если он является ребёнком обычного scope, его ошибка также участвует в Job-иерархии и отменяет родителя. `await()` дополнительно умеет повторно выбрасывать сохранённое исключение. ([Kotlin Discussions][4])

---

## Шаг 7. `color` не возвращает `"purple"`

В этот момент `color` находится здесь:

```kotlin
delay(1_000)
```

`delay` — cancellable suspending function. После отмены scope она прекращает ожидание с `CancellationException`. ([Kotlin][5])

Поэтому строка:

```kotlin
"purple"
```

не выполняется.

`color.await()` также является cancellable. Если ожидающая корутина отменяется, `await()` немедленно завершается с `CancellationException`. ([Kotlin][6])

---

## Шаг 8. До `catch` выполнение не доходит

`try-catch` находится только вокруг:

```kotlin
car.await()
```

Но программа всё ещё вычисляла левую часть:

```kotlin
color.await()
```

Поэтому этот блок не запускается:

```kotlin
try {
    car.await()
} catch (e: Exception) {
    "lamborghini"
}
```

И строка:

```kotlin
"lamborghini"
```

не создаётся.

---

## Шаг 9. `compute()` выбрасывает `IOException`

Хотя `color.await()` может промежуточно возобновиться с `CancellationException`, причиной провала всего `coroutineScope` остаётся исходный:

```kotlin
IOException
```

`coroutineScope` завершает детей и пробрасывает ошибку, которая привела к его падению.

Итог:

```text
compute() → IOException
println() → не вызывается
```

---

# 4. Почему внешний `lifecycleScope` не спасает

`lifecycleScope` создаётся с:

```kotlin
SupervisorJob() + Dispatchers.Main.immediate
```

А переданный в `launch`:

```kotlin
Dispatchers.IO
```

заменяет dispatcher, но не удаляет родительский `Job`. Реализация `lifecycleScope` действительно использует `SupervisorJob`. ([Android Git Repositories][7])

Но внутри `compute()` создаётся новый:

```kotlin
coroutineScope
```

Он **не supervisor**.

Получается такая иерархия:

```text
lifecycleScope
└── SupervisorJob
    └── launch(Dispatchers.IO)
        └── coroutineScope
            ├── async color
            └── async car
```

`SupervisorJob` на уровне `lifecycleScope` может защитить другие соседние `launch` от отмены:

```text
lifecycleScope
├── launch A  ← упал
└── launch B  ← может продолжить работать
```

Но он не делает автоматически supervisor-отношения между `color` и `car`:

```text
coroutineScope
├── color
└── car ← упал, поэтому color отменяется
```

Это важная деталь уровня Senior.

---

# 5. Ошибки и слабые места

## 1. `catch` стоит не на той границе

Разработчик, вероятно, рассуждал так:

```text
car.await() бросит IOException
→ catch поймает
→ вернётся "lamborghini"
```

Но исключение от child-корутины сначала влияет на родительский `coroutineScope`.

`try-catch` вокруг `await()` не восстанавливает уже упавший обычный родительский scope.

Даже такая перестановка не является корректным исправлением:

```kotlin
try {
    car.await()
} catch (e: IOException) {
    "lamborghini"
} + color.await()
```

Исключение можно поймать из `await()`, но обычный `coroutineScope` уже был отменён ошибкой своего ребёнка.

---

## 2. Слишком широкий `catch`

```kotlin
catch (e: Exception)
```

Он может поймать не только ожидаемый `IOException`, но и:

```kotlin
CancellationException
```

Это опасно для structured cancellation.

Лучше:

```kotlin
catch (e: IOException)
```

Если действительно необходимо ловить общие исключения, отмену нужно пробрасывать:

```kotlin
catch (e: CancellationException) {
    throw e
}
```

Официальная документация отдельно предупреждает, что перехват `CancellationException` может нарушить распространение отмены. ([Kotlin][8])

---

## 3. `async<String>` избыточен

Компилятору уже можно подсказать тип через дальнейшее использование или ожидаемый результат.

Но поскольку блок всегда бросает исключение, без контекста мог бы быть выведен `Deferred<Nothing>`. Поэтому явный `<String>` здесь, скорее всего, добавлен специально для задачи:

```kotlin
val car = async<String> {
    throw IOException()
}
```

Это также проверка понимания `Nothing`.

---

## 4. Для `delay` не нужен `Dispatchers.IO`

В коде нет блокирующей IO-операции — только:

```kotlin
delay(...)
```

`delay` не блокирует поток.

Поэтому ради этого примера достаточно:

```kotlin
lifecycleScope.launch {
    println(compute())
}
```

В production dispatcher лучше переключать непосредственно вокруг настоящего блокирующего API, а suspend-методы data/domain слоя делать main-safe.

---

# 6. Решение №1 — лучшее: обработать ожидаемую ошибку внутри child

Если падение `car` является ожидаемым и для него предусмотрен fallback, лучше превратить ошибку в успешный результат **внутри самого `async`**.

```kotlin
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

suspend fun compute(): String = coroutineScope {
    val colorDeferred = async {
        delay(1_000)
        "purple"
    }

    val carDeferred = async {
        try {
            delay(100)
            throw IOException()
        } catch (error: IOException) {
            "lamborghini"
        }
    }

    colorDeferred.await() + carDeferred.await()
}
```

Теперь `carDeferred` не завершается ошибкой:

```text
car → IOException
    → catch
    → "lamborghini"
    → успешное завершение Deferred
```

Через примерно одну секунду результат:

```text
purplelamborghini
```

### Плюсы

```text
ожидаемая ошибка обрабатывается рядом с источником
coroutineScope остаётся fail-fast для неожиданных ошибок
CancellationException не перехватывается
код достаточно простой
```

### Минус

Fallback становится частью внутренней логики задачи `car`.

---

# 7. Решение №2 — `supervisorScope`

Если `color` и `car` являются независимыми операциями и ошибка одной не должна отменять другую:

```kotlin
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope

suspend fun compute(): String = supervisorScope {
    val colorDeferred = async {
        delay(1_000)
        "purple"
    }

    val carDeferred = async<String> {
        delay(100)
        throw IOException()
    }

    val car = try {
        carDeferred.await()
    } catch (error: IOException) {
        "lamborghini"
    }

    val color = colorDeferred.await()

    color + car
}
```

Результат:

```text
purplelamborghini
```

Здесь ошибка `carDeferred` не отменяет `colorDeferred`, потому что `supervisorScope` позволяет детям падать независимо. ([Kotlin][9])

### Плюсы

```text
независимость параллельных операций
ошибка доступна через await
удобно для partial success
```

### Минусы

```text
можно случайно скрыть критическую ошибку
каждый Deferred нужно обязательно корректно обработать
не подходит, если результат имеет смысл только при успехе всех частей
```

---

# 8. Решение №3 — fail-fast и обработка на внешней границе

Если логика all-or-nothing:

```text
нет машины → весь compute невалиден
```

Тогда исходный `coroutineScope` правильный, но исключение нужно обрабатывать на вызывающей стороне.

```kotlin
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import timber.log.Timber

suspend fun computeOrThrow(): String = coroutineScope {
    val colorDeferred = async {
        delay(1_000)
        "purple"
    }

    val carDeferred = async<String> {
        delay(100)
        throw IOException()
    }

    colorDeferred.await() + carDeferred.await()
}

// Например, внутри Activity или Fragment:
fun startCompute() {
    lifecycleScope.launch(Dispatchers.IO) {
        try {
            val result = computeOrThrow()
            Timber.i("mylog 001 result=$result")
        } catch (error: IOException) {
            Timber.e(error, "mylog 002 compute failed")
        }
    }
}
```

### Плюсы

```text
чёткая all-or-nothing семантика
не возвращается ложный fallback
ошибка обрабатывается на границе use case / UI
```

### Минус

```text
при падении одной операции теряется успешный результат другой
```

---

# 9. Что именно проверяют на собеседовании

Главные ловушки этой задачи:

| Проверка                    | Правильное понимание                   |
| --------------------------- | -------------------------------------- |
| Когда стартуют `async`      | Оба начинают работу сразу              |
| Что делает `await`          | Ждёт результат или выбрасывает ошибку  |
| Что делает `coroutineScope` | Ошибка child отменяет scope и siblings |
| Отменится ли `color`        | Да                                     |
| Выполнится ли `"purple"`    | Нет                                    |
| Выполнится ли `catch`       | Нет, до него не дошли                  |
| Что вернёт `compute`        | Ничего, выбросит `IOException`         |
| Что напечатает `println`    | Ничего                                 |
| Зачем `<String>`            | `throw` имеет тип `Nothing`            |
| Спасает ли `lifecycleScope` | Нет, внутренний scope не supervisor    |

---

# 10. Сильный ответ для интервью

> Ничего не выведется. Оба `async` запускаются параллельно, после чего родитель ожидает `color.await()`. Через 100 миллисекунд `car` падает с `IOException`. Поскольку оба `async` являются детьми обычного `coroutineScope`, ошибка `car` отменяет родительский scope и sibling-корутину `color`. `color` отменяется во время `delay`, поэтому `color.await()` не возвращает `"purple"`, а до `try-catch` вокруг `car.await()` выполнение вообще не доходит. `coroutineScope` пробрасывает исходный `IOException`, `compute()` не возвращает строку, и `println` не вызывается. Для fallback я бы либо обработал ожидаемый `IOException` внутри `car`, либо использовал `supervisorScope`, если операции независимы.

# Шпаргалка 🧠

```text
coroutineScope
→ один child упал
→ остальные children отменяются
→ scope падает

supervisorScope
→ один child упал
→ siblings продолжают работать

async exception:
1. влияет на Job-иерархию;
2. повторно выбрасывается через await.

catch вокруг await
≠ восстановление уже упавшего coroutineScope

catch IOException
лучше, чем
catch Exception

Результат исходного кода:
ничего не напечатает
→ IOException
```

[1]: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/coroutine-scope.html?utm_source=chatgpt.com "coroutineScope | kotlinx.coroutines – Kotlin Programming Language"
[2]: https://kotlinlang.org/docs/exceptions.html?utm_source=chatgpt.com "Exception and error handling | Kotlin Documentation"
[3]: https://kotlinlang.org/spec/expressions.html?utm_source=chatgpt.com "Kotlin language specification"
[4]: https://discuss.kotlinlang.org/t/coroutine-async-exception-confusion-when-suspend-function-is-involved/24087?utm_source=chatgpt.com "Coroutine async exception confusion when suspend ..."
[5]: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/delay.html?utm_source=chatgpt.com "delay | kotlinx.coroutines – Kotlin Programming Language"
[6]: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-deferred/await.html?utm_source=chatgpt.com "await | kotlinx.coroutines – Kotlin Programming Language"
[7]: https://android.googlesource.com/platform/frameworks/support/%2B/965684cb54b60ae2177081628c8d17437e7db98c/lifecycle/lifecycle-common/src/main/java/androidx/lifecycle/Lifecycle.kt?utm_source=chatgpt.com "lifecycle/lifecycle-common/src/main/java/androidx/lifecycle/Lifecycle.kt - platform/frameworks/support - Git at Google"
[8]: https://kotlinlang.org/docs/cancellation-and-timeouts.html?utm_source=chatgpt.com "Cancellation and timeouts"
[9]: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-scope/?utm_source=chatgpt.com "CoroutineScope | kotlinx.coroutines – Kotlin Programming Language"








*/

