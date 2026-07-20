package com.example.study_project.tasks.fresh

// провести code review: найти ошибки компиляции, логические проблемы, проблемы с ключами Map, многопоточностью и читаемостью.

class ItemAnswer(
    val id: String,
    val communication: String,
)

class ItemAnswerProcessor {

    val hashMap = mapOf<ItemAnswer, String>()

    fun processItemAnswer(item: ItemAnswer) {
        if (hashMap[item] != null) {
//            return hashMap[item]
        } else {
//            hashMap[item] = item.generateItemId()
//            return hashMap[item]
        }
    }

    private fun ItemAnswer.generateItemId(): String {
        // TODO implementation
        return ""
    }
}


/*

Да, основная цель задачи — провести **code review**: найти ошибки компиляции, логические проблемы, проблемы с ключами `Map`, многопоточностью и читаемостью.

## Транскрипция кода

Код со скриншота без исправлений:

```kotlin
class Item(
    val id: String,
    val communication: String,
)

class ItemProcessor {

    val hashMap = mapOf<Item, String>()

    fun processItem(item: Item) {
        if (hashMap[item] != null) {
            return hashMap[item]
        } else {
            hashMap[item] = item.generateItemId()
            return hashMap[item]
        }
    }

    private fun Item.generateItemId(): String {
        // TODO implementation
    }
}
```

В таком виде код **не скомпилируется**.

---

# Что автор хотел сделать

По смыслу `ItemProcessor` пытается кешировать сгенерированный идентификатор:

```text
Пришёл Item
    ↓
Есть Item в Map?
    ├── да  → вернуть сохранённую строку
    └── нет → сгенерировать строку
              сохранить её
              вернуть
```

То есть `Map` используется как кеш:

```text
Item → generatedId
```

Например:

```text
Item(id="42") → "generated-abc"
```

При следующем вызове с тем же `Item` генерация не должна выполняться повторно.

---

# Ошибки компиляции

## 1. `mapOf()` создаёт read-only `Map`

```kotlin
val hashMap = mapOf<Item, String>()
```

Тип переменной:

```kotlin
Map<Item, String>
```

В него нельзя добавлять элементы:

```kotlin
hashMap[item] = value // Ошибка компиляции
```

Для изменения нужен `MutableMap`:

```kotlin
val hashMap = mutableMapOf<Item, String>()
```

или непосредственно:

```kotlin
val hashMap = HashMap<Item, String>()
```

`Map` предоставляет чтение, а `MutableMap` добавляет операции изменения: добавление, удаление и обновление пар ключ-значение. ([Kotlin][1])

### Важная разница между `val` и immutable

Такой код допустим:

```kotlin
val items = mutableMapOf<String, Int>()

items["one"] = 1
```

`val` запрещает заменить саму ссылку:

```kotlin
items = mutableMapOf() // Нельзя
```

Но не запрещает изменять объект, на который она указывает:

```kotlin
items["two"] = 2 // Можно
```

---

## 2. `processItem()` объявлен как функция, возвращающая `Unit`

Написано:

```kotlin
fun processItem(item: Item) {
```

Для функции с блочным телом и без указанного возвращаемого типа Kotlin использует `Unit`.

То есть декларация фактически означает:

```kotlin
fun processItem(item: Item): Unit {
```

Но внутри возвращается значение:

```kotlin
return hashMap[item]
```

Нужно явно написать:

```kotlin
fun processItem(item: Item): String {
```

---

## 3. `hashMap[item]` возвращает nullable-тип

Даже если значения имеют тип `String`, чтение из `Map` возвращает:

```kotlin
String?
```

Причина: такого ключа может не существовать.

```kotlin
val result: String? = hashMap[item]
```

Официальный контракт `Map.get()` возвращает значение либо `null`, если ключ отсутствует. ([Kotlin][1])

Поэтому такой код проблемный:

```kotlin
if (hashMap[item] != null) {
    return hashMap[item]
}
```

Первый и второй вызовы `hashMap[item]` — два отдельных чтения. Компилятор не обязан считать результат второго чтения non-null.

Лучше сохранить значение в переменную:

```kotlin
val existingId = hashMap[item]

if (existingId != null) {
    return existingId
}
```

---

## 4. `generateItemId()` ничего не возвращает

Функция обещает вернуть `String`:

```kotlin
private fun Item.generateItemId(): String {
```

Но внутри только комментарий:

```kotlin
// TODO implementation
```

Нужен настоящий результат:

```kotlin
private fun Item.generateItemId(): String {
    return "$id-$communication"
}
```

Для временной заглушки можно использовать:

```kotlin
private fun Item.generateItemId(): String {
    TODO("Implement item ID generation")
}
```

`TODO()` имеет тип `Nothing`, поэтому подходит как временная реализация функции с любым возвращаемым типом. При вызове она выбросит `NotImplementedError`.

---

# Логические слабости

## 5. `Item` — плохой ключ в текущем виде

Сейчас это обычный класс:

```kotlin
class Item(
    val id: String,
    val communication: String,
)
```

Обычный класс не генерирует структурные `equals()` и `hashCode()`. По умолчанию два объекта считаются равными только тогда, когда это один экземпляр. Data-классы, напротив, автоматически создают структурные `equals()` и `hashCode()`. ([Kotlin][2])

Пример:

```kotlin
val first = Item(
    id = "42",
    communication = "email",
)

val second = Item(
    id = "42",
    communication = "email",
)

println(first == second) // false
```

Несмотря на одинаковые поля, для `Map` это разные ключи:

```kotlin
val cache = mutableMapOf<Item, String>()

cache[first] = "generated-id"

println(cache[second]) // null
```

Получается неожиданный cache miss.

### Исправление через `data class`

```kotlin
data class Item(
    val id: String,
    val communication: String,
)
```

Теперь:

```kotlin
println(first == second) // true
```

Однако ещё лучше сначала решить, **что именно определяет идентичность Item**.

Если `id` уникален, разумнее сделать ключом только его:

```kotlin
MutableMap<String, String>
```

---

## 6. У `Item` уже есть `id`, но код генерирует ещё один ID

Модель содержит:

```kotlin
val id: String
```

При этом вызывается:

```kotlin
item.generateItemId()
```

Возникает вопрос:

> Зачем генерировать ID, если у объекта он уже есть?

Возможно, функция на самом деле генерирует не ID элемента, а:

* communication ID;
* request ID;
* tracking ID;
* processed ID;
* cache value.

Тогда название вводит в заблуждение.

Например:

```kotlin
generateProcessingToken()
```

или:

```kotlin
generateCommunicationId()
```

На интервью это хороший вопрос к автору задачи:

> Чем `Item.id` отличается от результата `generateItemId()`?

---

## 7. Название `hashMap` не соответствует объекту

```kotlin
val hashMap = mapOf<Item, String>()
```

Здесь переменная имеет интерфейсный тип `Map`, и фабрика `mapOf()` не обещает конкретно `HashMap`.

Кроме того, имя описывает техническую реализацию, а не смысл данных.

Плохо:

```kotlin
hashMap
```

Лучше:

```kotlin
generatedIdByItem
```

или:

```kotlin
generatedIdByItemId
```

По имени сразу видно:

```text
ключ   → itemId
значение → generatedId
```

---

## 8. Коллекция должна быть `private`

Сейчас:

```kotlin
val hashMap = ...
```

По умолчанию это `public`.

Любой внешний код потенциально сможет получить ссылку на коллекцию. Если она mutable, он сможет нарушить внутреннее состояние `ItemProcessor`.

Лучше:

```kotlin
private val generatedIdByItemId = mutableMapOf<String, String>()
```

Кеш — внутренняя деталь реализации класса.

---

## 9. Выполняется несколько поисков одного ключа

Исходный код обращается к `Map` до трёх раз:

```kotlin
if (hashMap[item] != null) {
    return hashMap[item]
} else {
    hashMap[item] = item.generateItemId()
    return hashMap[item]
}
```

Каждый вызов:

```kotlin
hashMap[item]
```

выполняет поиск по ключу.

Можно получить значение один раз:

```kotlin
val existingId = generatedIdByItem[item]

if (existingId != null) {
    return existingId
}
```

Но стандартная библиотека уже имеет подходящую операцию:

```kotlin
getOrPut()
```

Она возвращает существующее значение либо вычисляет, сохраняет и возвращает новое. ([Kotlin][1])

```kotlin
return generatedIdByItem.getOrPut(item) {
    item.generateItemId()
}
```

---

## 10. `else` после `return` не нужен

Исходный код:

```kotlin
if (existingId != null) {
    return existingId
} else {
    // ...
}
```

Поскольку первая ветка заканчивается `return`, `else` лишний:

```kotlin
if (existingId != null) {
    return existingId
}

val generatedId = item.generateItemId()
generatedIdByItem[item] = generatedId
return generatedId
```

Так код становится менее вложенным.

---

## 11. Возможна гонка данных

Представим, что два потока или две корутины одновременно вызывают:

```kotlin
processItem(item)
```

Оба могут увидеть отсутствие значения:

```text
Поток A: значения нет
Поток B: значения нет

Поток A: генерирует ID-1
Поток B: генерирует ID-2

Поток A: сохраняет ID-1
Поток B: сохраняет ID-2
```

В итоге:

* генерация выполнилась дважды;
* один результат был потерян;
* разные вызывающие получили разные идентификаторы.

Обычные реализации `MutableMap` не являются thread-safe без внешней синхронизации. ([Kotlin][1])

Это проблема только тогда, когда `processItem()` действительно может вызываться конкурентно.

---

## 12. Кеш растёт бесконечно

Каждый новый ключ остаётся в `Map`:

```text
1 000 Item   → 1 000 записей
100 000 Item → 100 000 записей
```

Если `ItemProcessor` живёт долго, это потенциальная утечка памяти на уровне дизайна.

Нужно определить политику:

* кеш нужен только на время одного экрана;
* кеш нужно периодически очищать;
* нужен ограниченный LRU-кеш;
* данные должны сохраняться в Room;
* значения вообще не нужно кешировать.

---

## 13. Данные исчезнут после завершения процесса

Обычный `Map` находится только в RAM.

После process death:

```text
Map → потерян
```

Если соответствие должно сохраняться между запусками приложения, in-memory `Map` недостаточно. Тогда нужен постоянный источник данных, например Room.

---

# Решение 1 — ключом является `Item.id`

Это наиболее понятный вариант при предположении, что `Item.id` уникален, а вызовы выполняются последовательно.

```kotlin
import java.util.UUID

data class Item(
    val id: String,
    val communication: String,
)

class ItemProcessor {

    private val generatedIdByItemId = mutableMapOf<String, String>()

    fun processItem(item: Item): String {
        return generatedIdByItemId.getOrPut(item.id) {
            generateProcessingId(item)
        }
    }

    private fun generateProcessingId(item: Item): String {
        return "${item.communication}-${UUID.randomUUID()}"
    }
}

fun main() {
    val processor = ItemProcessor()

    val firstItem = Item(
        id = "42",
        communication = "email",
    )

    val secondItem = Item(
        id = "42",
        communication = "email",
    )

    val firstResult = processor.processItem(firstItem)
    val secondResult = processor.processItem(secondItem)

    println(firstResult)
    println(secondResult)
    println(firstResult == secondResult) // true
}
```

Схема:

```text
"42" отсутствует
→ генерируем значение
→ сохраняем

"42" уже присутствует
→ возвращаем сохранённое значение
```

### Плюсы

* ключ простой и стабильный;
* нет зависимости от `equals()` всего объекта;
* понятная семантика;
* минимум кода.

### Ограничение

Если два `Item` с одинаковым `id`, но разным `communication` должны считаться разными, одного `id` недостаточно.

Тогда нужен составной ключ:

```kotlin
data class ItemKey(
    val id: String,
    val communication: String,
)
```

---

# Решение 2 — весь `Item` является ключом

Подходит, если вся комбинация полей определяет идентичность элемента.

```kotlin
import java.util.UUID

data class Item(
    val id: String,
    val communication: String,
)

class ItemProcessor {

    private val generatedIdByItem = mutableMapOf<Item, String>()

    fun processItem(item: Item): String {
        return generatedIdByItem.getOrPut(item) {
            UUID.randomUUID().toString()
        }
    }
}

fun main() {
    val processor = ItemProcessor()

    val firstResult = processor.processItem(
        Item(
            id = "42",
            communication = "email",
        ),
    )

    val secondResult = processor.processItem(
        Item(
            id = "42",
            communication = "email",
        ),
    )

    println(firstResult == secondResult) // true
}
```

Здесь `data class` обязателен для ожидаемого сравнения по полям.

### Ограничение

Все поля primary constructor участвуют в `equals()` и `hashCode()`:

```text
id одинаковый + communication разный
→ разные ключи
```

Это должно соответствовать бизнес-правилам.

---

# Решение 3 — конкурентный доступ

Если функция может вызываться одновременно из нескольких потоков, на JVM удобно использовать `ConcurrentHashMap`.

```kotlin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class Item(
    val id: String,
    val communication: String,
)

class ItemProcessor {

    private val generatedIdByItemId =
        ConcurrentHashMap<String, String>()

    fun processItem(item: Item): String {
        return generatedIdByItemId.computeIfAbsent(item.id) {
            UUID.randomUUID().toString()
        }
    }
}
```

`ConcurrentHashMap.computeIfAbsent()` атомарно проверяет ключ, вычисляет значение и сохраняет его. Для одного вызова функция вычисления вызывается только при отсутствии значения; вычисление должно быть коротким и не должно изменять ту же карту. ([Oracle Docs][3])

Если генерация является `suspend`-операцией, `computeIfAbsent()` не подходит. Тогда можно использовать `Mutex`:

```kotlin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class Item(
    val id: String,
    val communication: String,
)

class ItemProcessor {

    private val mutex = Mutex()

    private val generatedIdByItemId =
        mutableMapOf<String, String>()

    suspend fun processItem(item: Item): String {
        return mutex.withLock {
            generatedIdByItemId.getOrPut(item.id) {
                UUID.randomUUID().toString()
            }
        }
    }
}
```

Внутри этого `withLock` нельзя повторно захватывать тот же `Mutex`, поскольку coroutine `Mutex` нереентрантный.

---

# Что такое extension-функция в этом коде

```kotlin
private fun Item.generateItemId(): String
```

Это функция-расширение для `Item`.

Поэтому её можно вызвать так:

```kotlin
item.generateItemId()
```

Но она **не становится настоящим методом класса `Item`**. Она доступна только внутри `ItemProcessor`, потому что объявлена там как `private`.

В данном случае обычная функция, вероятно, читается яснее:

```kotlin
private fun generateProcessingId(item: Item): String
```

Потому что создание и кеширование результата относится к `ItemProcessor`, а не обязательно к самому `Item`.

---

# Как ответить на интервью

Хороший ответ:

> Код не компилируется. `mapOf` возвращает read-only `Map`, поэтому в него нельзя записывать. `processItem` не объявляет `String` как возвращаемый тип и фактически возвращает `Unit`. Кроме того, чтение через `map[item]` возвращает nullable-значение, а `generateItemId` не имеет реализации. Логически `Item` — обычный класс без структурных `equals` и `hashCode`, поэтому разные экземпляры с одинаковыми полями будут разными ключами. Я бы использовал стабильный `item.id` как ключ, приватный `MutableMap` и `getOrPut`. При конкурентном доступе понадобятся `ConcurrentHashMap.computeIfAbsent` или `Mutex`. Также нужно ограничить время жизни кеша.

Короткая шпаргалка:

```text
Компиляция:
❌ mapOf нельзя изменять
❌ processItem возвращает Unit
❌ Map.get возвращает String?
❌ generateItemId не возвращает String

Дизайн:
⚠ Item без equals/hashCode
⚠ публичный кеш
⚠ плохое название hashMap
⚠ повторные поиски
⚠ check-then-act race
⚠ бесконечный рост кеша
⚠ кеш теряется после process death
⚠ уже есть Item.id, но генерируется ещё один ID
```

[1]: https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/-mutable-map/ "MutableMap | Core API – Kotlin Programming Language"
[2]: https://kotlinlang.org/docs/equality.html "Equality | Kotlin Documentation"
[3]: https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html "ConcurrentHashMap (Java SE 26 & JDK 26)"


*/