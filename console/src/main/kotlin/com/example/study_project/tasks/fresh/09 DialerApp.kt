package com.example.study_project.tasks.fresh


//
//Загрузка телефона и открытие системной звонилки
//
//Условие
//
//Доступно API:
//
//https://api.yandex.net/offer/{id}/phones.json
//
//Формат ответа:
//
//{
//    "phones": [
//    "+7(999)-999-00-01",
//    "+7(999)-999-00-02"
//    ]
//}
//
//Есть готовый PhoneRepository, который обращается к API и возвращает список телефонных номеров.
//
//Реализацию репозитория писать не нужно.
//
//По нажатию на единственную кнопку на экране необходимо:
//
//Запросить номера по offerId.
//
//Выбрать номер для звонка.
//
//Открыть системную звонилку с полученным номером.
//
//Написать весь необходимый UI и бизнес-логику.
//
//Обосновать выбранную архитектуру и технологический стек.
//
//Других элементов интерфейса, кроме кнопки, на экране нет.
//
//Исходный контракт
//
//interface PhoneRepository {
//
//    suspend fun getPhones(id: String): List<String>
//}
//
//Ожидаемая структура
//
//UI
//→ PhoneViewModel
//→ GetPhoneForCallUseCase
//→ PhoneRepository
//
//PhoneRepository
//→ List<String>
//
//GetPhoneForCallUseCase
//→ выбирает один номер
//
//PhoneViewModel
//→ отправляет одноразовый эффект OpenDialer
//
//UI
//→ запускает Intent.ACTION_DIAL
//
//Минимальный каркас
//
//import javax.inject.Inject
//
//class GetPhoneForCallUseCase @Inject constructor(
//    private val phoneRepository: PhoneRepository,
//) {
//
//    suspend operator fun invoke(offerId: String): String {
//        TODO()
//    }
//}
//
//import androidx.lifecycle.ViewModel
//import javax.inject.Inject
//
//class PhoneViewModel @Inject constructor(
//    private val getPhoneForCallUseCase: GetPhoneForCallUseCase,
//) : ViewModel() {
//
//    fun onCallClicked(offerId: String) {
//        TODO()
//    }
//}
//
//@Composable
//fun PhoneScreen(
//    offerId: String,
//    viewModel: PhoneViewModel,
//) {
//    // На экране должна быть одна кнопка.
//}
//
//Важное поведение
//
//повторное нажатие во время загрузки не должно создавать дублирующий запрос;
//
//пустой список телефонов должен обрабатываться как ошибка;
//
//отмена корутины не должна превращаться в обычную UI-ошибку;
//
//открытие звонилки является одноразовым UI-эффектом;
//
//используется Intent.ACTION_DIAL, а не автоматический звонок через ACTION_CALL.
//
//