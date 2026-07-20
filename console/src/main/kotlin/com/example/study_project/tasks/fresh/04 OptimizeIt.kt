package com.example.study_project.tasks.fresh

// понять что делает эта функция и и узнать ее

/*

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

*/


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

/*

Как ответить на интервью

Хороший ответ:

Код не компилируется. mapOf возвращает read-only Map, поэтому в него нельзя записывать.
processItem не объявляет String как возвращаемый тип и фактически возвращает Unit.
Кроме того, чтение через map[item] возвращает nullable-значение, а generateItemId не имеет реализации.

Логически Item — обычный класс без структурных equals и hashCode, поэтому разные экземпляры с одинаковыми полями будут разными ключами.
Я бы использовал стабильный item.id как ключ, приватный MutableMap и getOrPut.
При конкурентном доступе понадобятся ConcurrentHashMap.computeIfAbsent или Mutex.
Также нужно ограничить время жизни кеша.

*/