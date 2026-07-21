package com.example.study_project.tasks.fresh












/*


Исходный код:

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private lateinit var handler: Handler
    private lateinit var textView: TextView
    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView = findViewById(R.id.text_view)
        button = findViewById(R.id.button)

        handler = Handler()

        button.setOnClickListener {
            handler.postDelayed(
                {
                    textView.text = "Task completed after delay"
                    someLongOperation()
                },
                5_000,
            )
        }
    }

    override fun onResume() {
        handler.postDelayed(
            {
                val thread = thread(start = false) {
                    MyTask().run()
                }

                thread.start()
            },
            500,
        )
    }

    private fun someLongOperation() {
        // Make network request.
    }

    private inner class MyTask : Runnable {

        override fun run() {
            if (::textView.isInitialized) {
                textView.text = "Task from inner class"
            }
        }
    }
}


Сильный ответ для интервью

В текущем виде код сначала упадёт из-за отсутствия super.onResume().
Если это исправить, останутся две противоположные threading-ошибки.
Handler, созданный в onCreate, связан с main looper, поэтому someLongOperation()
после postDelayed выполняется на main thread и может вызвать NetworkOnMainThreadException или ANR.
При этом MyTask запускается в отдельном потоке и напрямую изменяет TextView, что нарушает правило доступа
к UI только с main thread. Дополнительно delayed callbacks не отменяются, повторные onResume и клики накапливают задачи,
а inner class и лямбды удерживают Activity. Для минимального исправления я бы использовал lifecycleScope, delay и
main-safe suspend-функцию. В production вынес бы операцию в Repository и ViewModel, а UI отображал бы через StateFlow
как единый источник состояния.


Шпаргалка 🧠

Handler.postDelayed
≠ background thread

Handler(MainLooper)
→ Runnable работает на main

Network на main
→ NetworkOnMainThreadException / ANR

View update из worker
→ CalledFromWrongThreadException

override lifecycle callback
→ вызвать super

inner class
→ хранит ссылку на outer class

lateinit.isInitialized
→ проверяет только факт присваивания

Activity UI task
→ lifecycleScope

Screen state
→ ViewModel + StateFlow

Гарантированная фоновая работа
→ отдельный persistent-механизм, не Activity Thread

*/






/* Решение на корутинах (надо вкл. вью биндинг)

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.app.databinding.ActivityMainBinding
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var buttonProcessingJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        observeResumedState()

        binding.button.setOnClickListener {
            processButtonClick()
        }
    }

    private fun observeResumedState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                delay(500)

                binding.textView.text = "Task from resumed state"
            }
        }
    }

    private fun processButtonClick() {
        if (buttonProcessingJob?.isActive == true) {
            return
        }

        buttonProcessingJob = lifecycleScope.launch {
            delay(5_000)

            binding.textView.text = "Task started"

            try {
                someLongOperation()

                binding.textView.text = "Task completed after delay"
            } catch (error: IOException) {
                Timber.e(
                    error,
                    "mylog 001 task failed",
                )

                binding.textView.text = "Task failed"
            }
        }
    }

    private suspend fun someLongOperation() {
        withContext(Dispatchers.IO) {
            // Только для блокирующего API:
            // blockingNetworkClient.execute()
        }
    }
}

*/








/*

Да 👍 Здесь проверяют **Android lifecycle, Handler/Looper, main thread, raw Thread, memory leaks и отмену задач**.

Главная ловушка: код не просто «неоптимальный» — в текущем виде приложение, вероятнее всего, **упадёт уже при первом `onResume()`**.

# 1. Код со скриншота

```kotlin
class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private lateinit var handler: Handler
    private lateinit var textView: TextView
    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView = findViewById(R.id.text_view)
        button = findViewById(R.id.button)

        handler = Handler()

        button.setOnClickListener {
            handler.postDelayed(
                {
                    textView.text = "Task completed after delay"
                    someLongOperation()
                },
                5_000,
            )
        }
    }

    override fun onResume() {
        handler.postDelayed(
            {
                val thread = thread(start = false) {
                    MyTask().run()
                }

                thread.start()
            },
            500,
        )
    }

    private fun someLongOperation() {
        // Make network request.
    }

    private inner class MyTask : Runnable {

        override fun run() {
            if (::textView.isInitialized) {
                textView.text = "Task from inner class"
            }
        }
    }
}
```

Предполагаемый замысел:

```text
Нажатие кнопки
→ подождать 5 секунд
→ изменить TextView
→ выполнить сетевой запрос

onResume
→ подождать 500 мс
→ запустить отдельный поток
→ изменить TextView
```

# 2. Компилируется ли код

При наличии импортов и соответствующих XML-ресурсов код в основном **скомпилируется**.

Но будут проблемы:

```kotlin
handler = Handler()
```

Конструктор без явного `Looper` deprecated. Это warning, а не ошибка компиляции. Android рекомендует явно передавать `Looper` или использовать `Executor`/coroutines, потому что неявный выбор потока может приводить к race condition, потере задач и падениям. ([Android Developers][1])

```kotlin
override fun onResume() {
    // Нет super.onResume()
}
```

Это также компилируется, но является серьёзной runtime-ошибкой.

# 3. Что произойдёт при запуске

## Шаг 1. Выполняется `onCreate`

`Activity.onCreate()` вызывается на main thread.

```kotlin
handler = Handler()
```

Старый конструктор связывает `Handler` с `Looper` текущего потока. Поскольку `onCreate()` выполняется на main thread, `handler` будет связан с `MainLooper`. Все его `post` и `postDelayed` будут выполняться на main thread. ([Android Developers][1])

Упрощённо:

```text
handler
    ↓
MainLooper
    ↓
Main MessageQueue
    ↓
Main thread
```

Важно:

```kotlin
handler.postDelayed { ... }
```

не создаёт background thread. Он только откладывает выполнение `Runnable` в очереди указанного `Looper`.

## Шаг 2. Вызывается `onResume`

```kotlin
override fun onResume() {
    handler.postDelayed(...)
}
```

Но здесь отсутствует:

```kotlin
super.onResume()
```

Реализация `Activity` требует вызвать superclass implementation. Если этого не сделать, Android выбросит исключение. ([Android Developers][2])

То есть реальный результат исходного кода:

```text
onCreate
→ onResume
→ postDelayed добавлен в очередь
→ super.onResume не вызван
→ Activity падает
```

До выполнения задачи через 500 мс приложение, скорее всего, вообще не дойдёт.

Минимальное исправление:

```kotlin
override fun onResume() {
    super.onResume()

    handler.postDelayed(
        {
            // ...
        },
        500,
    )
}
```

Но даже после этого останутся другие ошибки.

# 4. Ошибка: UI обновляется из background thread

После 500 мс на main thread выполняется:

```kotlin
val thread = thread(start = false) {
    MyTask().run()
}

thread.start()
```

`thread.start()` действительно создаёт новый поток.

Внутри этого background thread вызывается:

```kotlin
MyTask().run()
```

А затем:

```kotlin
textView.text = "Task from inner class"
```

Это обновление Android View из background thread.

Android UI toolkit не является thread-safe. Действуют два базовых правила:

```text
1. Не блокировать main thread.
2. Не обращаться к View из background thread.
```

UI необходимо изменять только на main thread. ([Android Developers][3])

Поэтому возможен runtime crash наподобие:

```text
CalledFromWrongThreadException:
Only the original thread that created a view hierarchy
can touch its views
```

## Почему `isInitialized` не помогает

```kotlin
if (::textView.isInitialized) {
    textView.text = ...
}
```

Эта проверка отвечает только на вопрос:

```text
Было ли lateinit-поле когда-либо инициализировано?
```

Она не проверяет:

```text
на каком потоке выполняется код;
существует ли ещё Activity;
уничтожена ли Activity;
прикреплён ли TextView к окну;
актуальна ли эта View после rotation.
```

Более того, здесь `textView` инициализируется в `onCreate` до первого `onResume`, поэтому в нормальном lifecycle проверка практически всегда будет `true`.

# 5. Ошибка: network request выполняется на main thread

При нажатии кнопки:

```kotlin
handler.postDelayed(
    {
        textView.text = "Task completed after delay"
        someLongOperation()
    },
    5_000,
)
```

Так как `handler` связан с `MainLooper`, весь блок через пять секунд выполняется на main thread.

Следовательно:

```kotlin
someLongOperation()
```

тоже выполняется на main thread.

Если внутри находится настоящий блокирующий network request, возможны:

```text
NetworkOnMainThreadException
ANR
зависший UI
пропущенные кадры
неработающие нажатия
```

Android прямо запрещает выполнять сетевые операции на UI thread; для обычной сетевой операции может быть выброшен `NetworkOnMainThreadException`. ([Android Developers][4])

## Ещё одна скрытая проблема

Сначала выполняется:

```kotlin
textView.text = "Task completed after delay"
```

а затем main thread блокируется:

```kotlin
someLongOperation()
```

Хотя значение свойства уже изменено, Android может не успеть отрисовать новый текст. Отрисовка произойдёт только после освобождения main thread.

Пользователь может увидеть текст лишь после завершения долгой операции.

Кроме того, сообщение логически неверное:

```text
"Task completed"
```

показывается **до** выполнения операции.

Правильнее:

```text
Task started
→ network request
→ Task completed
```

# 6. Ошибка: задачи не отменяются

## Callback после клика

Пользователь нажал кнопку:

```text
postDelayed на 5 секунд
```

Затем:

```text
повернул экран;
закрыл Activity;
перешёл на другой экран.
```

Старый callback продолжит находиться в `MessageQueue`.

Runnable обращается к полям Activity:

```kotlin
textView
someLongOperation()
```

Поэтому очередь может временно удерживать старый экземпляр `MainActivity`.

Последствия:

```text
утечка старой Activity до выполнения callback;
обращение к устаревшей View;
запуск ненужной операции;
несколько параллельных запросов.
```

У `Handler` есть `removeCallbacks` и `removeCallbacksAndMessages`, предназначенные для удаления ожидающих задач. ([Android Developers][1])

## Callback из `onResume`

Каждый новый `onResume()` добавляет ещё один callback:

```text
onResume №1 → callback A
onPause
onResume №2 → callback B
```

Callback A не удаляется.

В результате могут стартовать два потока:

```text
thread A
thread B
```

Оба попытаются изменить один `TextView`.

# 7. Ошибка: unmanaged raw Thread

```kotlin
val thread = thread(start = false) {
    MyTask().run()
}

thread.start()
```

Проблемы:

```text
не связан с lifecycle Activity;
не отменяется в onPause/onDestroy;
не имеет structured concurrency;
нет нормальной обработки ошибок;
каждый вызов создаёт новый OS thread;
поток может пережить Activity.
```

Также запись избыточна:

```kotlin
val thread = thread(start = false) {
    MyTask().run()
}

thread.start()
```

Можно было написать:

```kotlin
thread {
    MyTask().run()
}
```

или:

```kotlin
Thread(MyTask()).start()
```

Но архитектурную проблему это не решает.

## Что означает `.run()`

```kotlin
MyTask().run()
```

`run()` сам по себе новый поток не создаёт. Он синхронно выполняется на том потоке, который его вызвал.

В данном коде он вызывается внутри нового потока:

```kotlin
thread {
    MyTask().run()
}
```

поэтому `MyTask.run()` работает в worker thread.

# 8. Ошибка: `inner class` удерживает Activity

```kotlin
private inner class MyTask : Runnable
```

`inner` означает, что экземпляр `MyTask` содержит неявную ссылку на внешний:

```kotlin
MainActivity
```

Концептуально:

```kotlin
class MyTask(
    private val activity: MainActivity,
)
```

Пока живёт `MyTask` или запущенный поток, они могут удерживать `Activity`.

Анонимные лямбды `postDelayed`, которые обращаются к полям Activity, тоже захватывают `this@MainActivity`.

# 9. Race condition на уровне UI

Есть несколько независимых источников изменения текста:

```kotlin
textView.text = "Task completed after delay"
```

и:

```kotlin
textView.text = "Task from inner class"
```

Их порядок зависит от событий:

```text
onResume;
момент нажатия;
несколько нажатий;
повторные onResume;
скорость потоков.
```

Финальный текст недетерминирован.

Например:

```text
0 мс    → onResume
500 мс  → "Task from inner class"
1 сек   → пользователь нажал кнопку
6 сек   → "Task completed after delay"
```

Но при повторных нажатиях и resume порядок изменится.

Для production-кода UI должен получать состояние из одного источника, например:

```text
ViewModel StateFlow = SSOT
```

# 10. Менее критичные проблемы

```kotlin
private val TAG = "MainActivity"
```

Переменная не используется.

Для классического Android logging обычно было бы:

```kotlin
private companion object {
    const val TAG = "MainActivity"
}
```

При использовании Timber собственный `TAG` чаще вообще не нужен.

Название:

```kotlin
textView
```

лучше, чем `textview`, но ещё лучше описывать назначение:

```kotlin
statusTextView
```

Также современнее использовать View Binding вместо нескольких `findViewById`.

---

# Решение 1 — лучшее для этой конкретной задачи: coroutines + lifecycle

Убираем:

```text
Handler
Thread
Runnable
inner class
ручное переключение на UI thread
```

Предполагается, что включён View Binding.

```kotlin
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.app.databinding.ActivityMainBinding
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var buttonProcessingJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        observeResumedState()

        binding.button.setOnClickListener {
            processButtonClick()
        }
    }

    private fun observeResumedState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                delay(500)

                binding.textView.text = "Task from resumed state"
            }
        }
    }

    private fun processButtonClick() {
        if (buttonProcessingJob?.isActive == true) {
            return
        }

        buttonProcessingJob = lifecycleScope.launch {
            delay(5_000)

            binding.textView.text = "Task started"

            try {
                someLongOperation()

                binding.textView.text = "Task completed after delay"
            } catch (error: IOException) {
                Timber.e(
                    error,
                    "mylog 001 task failed",
                )

                binding.textView.text = "Task failed"
            }
        }
    }

    private suspend fun someLongOperation() {
        withContext(Dispatchers.IO) {
            // Только для блокирующего API:
            // blockingNetworkClient.execute()
        }
    }
}
```

`lifecycleScope` автоматически отменяется при уничтожении lifecycle. `repeatOnLifecycle` запускает блок при достижении нужного состояния и отменяет его, когда lifecycle выходит из этого состояния. ([Android Developers][5])

## Пошагово

```text
Activity становится RESUMED
→ запускается блок repeatOnLifecycle
→ delay(500)
→ TextView обновляется на main thread

Activity уходит в PAUSED
→ текущий блок отменяется

Activity снова RESUMED
→ блок запускается заново
```

Для кнопки:

```text
click
→ lifecycleScope.launch
→ delay(5 секунд), не блокируя поток
→ показать Task started
→ перейти на Dispatchers.IO
→ выполнить блокирующую операцию
→ вернуться на main thread
→ обновить TextView
```

### Важное уточнение про Retrofit

Если используется Retrofit `suspend` API:

```kotlin
interface Api {
    @GET("task")
    suspend fun executeTask()
}
```

обычно не нужно вручную оборачивать вызов в `Dispatchers.IO`:

```kotlin
private suspend fun someLongOperation() {
    api.executeTask()
}
```

`withContext(Dispatchers.IO)` нужен для действительно блокирующего API.

### Trade-offs

Плюсы:

```text
lifecycle-aware;
нет raw Thread;
delay не блокирует поток;
простая отмена;
UI изменяется на main;
защита от повторных нажатий.
```

Минусы:

```text
состояние находится внутри Activity;
операция не переживёт configuration change;
бизнес-логика смешана с UI.
```

---

# Решение 2 — production: ViewModel + StateFlow + Repository

Для реального приложения сетевую операцию лучше вынести из Activity.

```text
Activity
    ↓ events
ViewModel
    ↓
Repository
    ↓
API

Repository/ViewModel
    ↓ StateFlow
Activity
```

## UI state

```kotlin
sealed interface MainUiState {

    data object Idle : MainUiState

    data object Waiting : MainUiState

    data object Loading : MainUiState

    data class Content(
        val message: String,
    ) : MainUiState

    data class Error(
        val message: String,
    ) : MainUiState
}
```

## Repository

```kotlin
interface TaskRepository {

    suspend fun executeTask()
}
```

Пример реализации для блокирующего клиента:

```kotlin
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class TaskRepositoryImpl @Inject constructor(
    private val blockingTaskApi: BlockingTaskApi,
) : TaskRepository {

    override suspend fun executeTask() {
        withContext(Dispatchers.IO) {
            blockingTaskApi.executeTask()
        }
    }
}

interface BlockingTaskApi {

    @Throws(java.io.IOException::class)
    fun executeTask()
}
```

## ViewModel

```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class MainViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
) : ViewModel() {

    private val _mainUiState =
        MutableStateFlow<MainUiState>(MainUiState.Idle)

    val mainUiState: StateFlow<MainUiState> =
        _mainUiState.asStateFlow()

    private var buttonProcessingJob: Job? = null

    fun onButtonClick() {
        if (buttonProcessingJob?.isActive == true) {
            return
        }

        buttonProcessingJob = viewModelScope.launch {
            _mainUiState.value = MainUiState.Waiting

            delay(5_000)

            _mainUiState.value = MainUiState.Loading

            try {
                taskRepository.executeTask()

                _mainUiState.value = MainUiState.Content(
                    message = "Task completed after delay",
                )
            } catch (error: IOException) {
                Timber.e(
                    error,
                    "mylog 002 task failed",
                )

                _mainUiState.value = MainUiState.Error(
                    message = "Task failed",
                )
            }
        }
    }

    fun onResumeDelayCompleted() {
        if (_mainUiState.value == MainUiState.Idle) {
            _mainUiState.value = MainUiState.Content(
                message = "Task from resumed state",
            )
        }
    }
}
```

## Activity

```kotlin
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.app.databinding.ActivityMainBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // ViewModelProvider.Factory предоставляет Dagger 2.
    private val viewModel: MainViewModel by viewModels {
        viewModelFactory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.button.setOnClickListener {
            viewModel.onButtonClick()
        }

        collectUiState()
        observeResumedState()
    }

    private fun collectUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.mainUiState.collect { mainUiState ->
                    render(mainUiState)
                }
            }
        }
    }

    private fun observeResumedState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                delay(500)
                viewModel.onResumeDelayCompleted()
            }
        }
    }

    private fun render(mainUiState: MainUiState) {
        when (mainUiState) {
            MainUiState.Idle -> {
                binding.textView.text = ""
                binding.button.isEnabled = true
            }

            MainUiState.Waiting -> {
                binding.textView.text = "Waiting"
                binding.button.isEnabled = false
            }

            MainUiState.Loading -> {
                binding.textView.text = "Loading"
                binding.button.isEnabled = false
            }

            is MainUiState.Content -> {
                binding.textView.text = mainUiState.message
                binding.button.isEnabled = true
            }

            is MainUiState.Error -> {
                binding.textView.text = mainUiState.message
                binding.button.isEnabled = true
            }
        }
    }
}
```

`viewModelScope` отменяется, когда `ViewModel` окончательно очищается, а сам ViewModel позволяет сохранить UI-related состояние при configuration change. ([Android Developers][5])

### Trade-offs

Плюсы:

```text
SSOT;
UDF;
сохраняется при rotation;
Activity занимается только rendering;
удобно тестировать;
Repository main-safe;
структурированная отмена.
```

Минусы:

```text
больше классов;
нужен ViewModel factory;
нужно продумать конкуренцию UI-событий;
viewModelScope не переживает process death.
```

---

# Решение 3 — минимальный legacy fix через Handler + Executor

Такой вариант подходит, если на интервью просят исправить код, не переходя на coroutines.

```kotlin
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val workerExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()

    private lateinit var textView: TextView
    private lateinit var button: Button

    private val resumeRunnable = Runnable {
        textView.text = "Task from resumed state"
    }

    private val buttonRunnable = Runnable {
        workerExecutor.execute {
            try {
                someLongOperationBlocking()

                mainHandler.post {
                    if (!isFinishing && !isDestroyed) {
                        textView.text = "Task completed after delay"
                    }
                }
            } catch (error: IOException) {
                Timber.e(
                    error,
                    "mylog 003 task failed",
                )

                mainHandler.post {
                    if (!isFinishing && !isDestroyed) {
                        textView.text = "Task failed"
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView = findViewById(R.id.text_view)
        button = findViewById(R.id.button)

        button.setOnClickListener {
            mainHandler.removeCallbacks(buttonRunnable)

            textView.text = "Waiting"

            mainHandler.postDelayed(
                buttonRunnable,
                5_000,
            )
        }
    }

    override fun onResume() {
        super.onResume()

        mainHandler.removeCallbacks(resumeRunnable)
        mainHandler.postDelayed(
            resumeRunnable,
            500,
        )
    }

    override fun onPause() {
        mainHandler.removeCallbacks(resumeRunnable)

        super.onPause()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        workerExecutor.shutdownNow()

        super.onDestroy()
    }

    @Throws(IOException::class)
    private fun someLongOperationBlocking() {
        // Blocking network request.
    }
}
```

### Что исправлено

```text
Handler явно связан с MainLooper;
super.onResume вызван;
network выполняется в Executor;
UI обновляется через mainHandler;
нет inner Runnable;
callback удаляется;
повторный click заменяет предыдущий;
Executor закрывается.
```

### Trade-offs

Плюсы:

```text
минимальные изменения;
подходит для legacy-кода;
демонстрирует Handler/Looper.
```

Минусы:

```text
много ручного управления;
нет structured concurrency;
сложнее cancellation;
блокирующий request может проигнорировать interrupt;
Activity всё ещё управляет бизнес-логикой.
```

Android рекомендует вместо ручных HandlerThread-подходов предпочитать `Executor` или Kotlin coroutines, когда это возможно. ([Android Developers][6])

# Что проверяют на собеседовании

| Место                      | Ожидаемый ответ                                  |
| -------------------------- | ------------------------------------------------ |
| `Handler()`                | Deprecated, Looper выбирается неявно             |
| `postDelayed`              | Не создаёт background thread                     |
| `someLongOperation()`      | Выполняется на main thread                       |
| Network на main            | Exception/ANR                                    |
| Нет `super.onResume()`     | Runtime crash                                    |
| `MyTask.run()`             | Выполняется на созданном worker thread           |
| `textView.text` в `MyTask` | UI update с неправильного потока                 |
| `inner class`              | Удерживает ссылку на Activity                    |
| `isInitialized`            | Не проверяет lifecycle и поток                   |
| Повторный `onResume`       | Создаёт дополнительные callbacks                 |
| Повторный click            | Создаёт дополнительные операции                  |
| `onDestroy`                | Callback и thread не отменяются                  |
| Rotation                   | Старые задачи могут обращаться к старой Activity |

# Сильный ответ для интервью

> В текущем виде код сначала упадёт из-за отсутствия `super.onResume()`. Если это исправить, останутся две противоположные threading-ошибки. `Handler`, созданный в `onCreate`, связан с main looper, поэтому `someLongOperation()` после `postDelayed` выполняется на main thread и может вызвать `NetworkOnMainThreadException` или ANR. При этом `MyTask` запускается в отдельном потоке и напрямую изменяет `TextView`, что нарушает правило доступа к UI только с main thread. Дополнительно delayed callbacks не отменяются, повторные `onResume` и клики накапливают задачи, а `inner class` и лямбды удерживают Activity. Для минимального исправления я бы использовал `lifecycleScope`, `delay` и main-safe suspend-функцию. В production вынес бы операцию в Repository и ViewModel, а UI отображал бы через `StateFlow` как единый источник состояния.

# Шпаргалка 🧠

```text
Handler.postDelayed
≠ background thread

Handler(MainLooper)
→ Runnable работает на main

Network на main
→ NetworkOnMainThreadException / ANR

View update из worker
→ CalledFromWrongThreadException

override lifecycle callback
→ вызвать super

inner class
→ хранит ссылку на outer class

lateinit.isInitialized
→ проверяет только факт присваивания

Activity UI task
→ lifecycleScope

Screen state
→ ViewModel + StateFlow

Гарантированная фоновая работа
→ отдельный persistent-механизм, не Activity Thread
```

[1]: https://developer.android.com/reference/android/os/Handler "Handler  |  API reference  |  Android Developers"
[2]: https://developer.android.com/reference/android/app/Activity.html "Activity  |  API reference  |  Android Developers"
[3]: https://developer.android.com/guide/components/processes-and-threads "Processes and threads overview  |  App quality  |  Android Developers"
[4]: https://developer.android.com/develop/connectivity/network-ops/connecting "Connect to the network  |  Connectivity  |  Android Developers"
[5]: https://developer.android.com/topic/libraries/architecture/views/coroutines-views "Use Kotlin coroutines with lifecycle-aware components (Views)  |  Android Developers"
[6]: https://developer.android.com/reference/kotlin/android/os/HandlerThread?utm_source=chatgpt.com "HandlerThread | API reference"


*/



