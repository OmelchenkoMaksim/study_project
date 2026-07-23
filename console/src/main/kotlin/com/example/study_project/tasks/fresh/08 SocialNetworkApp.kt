package com.example.study_project.tasks.fresh
//
//
//Репозиторий публикаций: in-memory cache, refresh и upload
//
//Условие
//
//Приложение — социальная сеть с публикациями.
//
//Публикация состоит из текста и приложенных изображений.
//
//Требуется реализовать методы репозитория публикаций:
//
//observePosts() — позволять наблюдать за публикациями, находящимися в in-memory cache.
//
//refreshPosts() — асинхронно обновлять кэш данными из сети.
//
//Считать кэш актуальным в течение 5 секунд после очередного успешного refresh.
//
//uploadPost() — загрузить локальные изображения, получить их remote URL, а затем загрузить публикацию.
//
//Разрешается изменять класс PostsRepository как угодно, кроме исходного публичного контракта его методов.
//
//Исходный код
//
//// Приложение — социальная сеть с публикациями.
//// Публикация состоит из текста и приложенных изображений.
////
//// Требуется реализовать методы репозитория публикаций:
////
//// 1. observePosts() — уметь наблюдать за публикациями в in-memory кэше.
//// 2. refreshPosts() — асинхронно актуализировать кэш данными из сети.
//// 3. Считать кэш актуальным 5 секунд после очередного refresh.
//// 4. uploadPost() — реализовать метод загрузки публикации.
////
//// Разрешается модифицировать класс как угодно,
//// за исключением исходного контракта.
//
//data class PostInput(
//    val description: String,
//    val localImages: List<String>,
//)
//
//data class Post(
//    val id: String,
//    val description: String,
//    val remoteImages: List<String>,
//)
//
//interface PostsDataSource {
//
//    suspend fun getPosts(): List<Post>
//
//    suspend fun uploadImage(
//        localImage: String,
//    ): String // remote url
//
//    suspend fun uploadPost(
//        description: String,
//        remoteImages: List<String>,
//    ): String // id
//}
//
//class PostsRepository(
//    private val dataSource: PostsDataSource,
//) {
//
//    fun observePosts(): Flow<List<Post>> {
//        TODO()
//    }
//
//    fun refreshPosts() {
//        TODO()
//    }
//
//    suspend fun uploadPost(input: PostInput): String {
//        TODO()
//    }
//}
//
//Основные кейсы, которые должна покрывать реализация
//
//observePosts()
//→ новый подписчик сразу получает текущее значение кэша
//→ после refresh получает обновлённый список
//
//refreshPosts()
//→ выполняется асинхронно
//→ несколько одновременных вызовов не создают несколько одинаковых запросов
//→ при актуальном кэше новый запрос не выполняется
//→ TTL обновляется только после успешного запроса
//
//uploadPost()
//→ каждое localImage загружается через uploadImage()
//→ полученные URL передаются в uploadPost()
//→ метод возвращает id созданной публикации
//→ при ошибке одного изображения вся операция завершается ошибкой
//
//