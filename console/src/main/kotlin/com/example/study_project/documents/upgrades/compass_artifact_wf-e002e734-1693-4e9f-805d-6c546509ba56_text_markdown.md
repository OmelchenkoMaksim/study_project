# Senior Android Knowledge Base — ЧАСТЬ 2 (2026): гочи, ловушки и калибровка уровня

Главный тезис этой части: на senior-собеседовании 2026 года кандидата отделяют от middle не знанием API, а пониманием того, **где книжный ответ незаметно неверен** — от сломанного `equals()` у data class с массивом и `synchronized` через suspension point до того, что стектрейс ANR систематически врёт, а Android 16 молча игнорирует `screenOrientation="portrait"` на планшетах. Ниже — 10 тем, для каждой теория (не-очевидное), калибровочные вопросы с разбором Junior/Middle/Senior и 6 практических задач-ловушек.

## TL;DR
- **Самые разоблачающие гочи 2026**, по темам: (1) `equals()` у `data class` с полем-массивом сравнивает массив по ссылке, а не по содержимому; (2) `synchronized` через suspension point — это баг корректности, а не перформанса, потому что монитор JVM привязан к потоку, а корутина меняет поток; (3) стектрейс ANR указывает на Activity, хотя main занял BroadcastReceiver до неё; (4) `shouldShowRequestPermissionRationale` не различает «не спрашивали» и «отказано навсегда».
- **Актуальное на платформе 2026**: Android 16 (API 36) на экранах sw ≥ 600dp игнорирует ограничения ориентации/resizability для приложений с targetSdk 36 (opt-out убирается в API 37); Google Play требует targeting API 36 с 31 августа 2026; permission auto-reset отзывает разрешения после ~90 дней неиспользования; PendingIntent-флаги мутабельности обязательны с API 31; KSP2 — дефолт с начала 2025, KSP1 несовместим с Kotlin 2.3.0+/AGP 9.0+.
- **Что раскрывает реальный опыт**: знание, что предупреждение о массиве в data class даёт IDE-инспекция, а не компилятор; что `Mutex` нереентрантный; что `receiveAsFlow` без закрытия канала течёт; что buildSrc инвалидирует configuration cache для всей сборки; что App Startup сам по себе не ускоряет старт, если оставить всё eager.

## Key Findings
- **Kotlin**: extension-функции статически диспетчеризуются (по объявленному типу); `data class` с `Array` даёт референсное `equals()` → нужен ручной override через `contentEquals()`/`contentHashCode()`; `Mutex` suspension-safe и **нереентрантный**; Channel-capacity (RENDEZVOUS/BUFFERED/CONFLATED/UNLIMITED) и различие `consumeAsFlow` (1 коллектор, авто-закрытие) vs `receiveAsFlow` (N коллекторов, ручное закрытие).
- **Permissions**: третье состояние медиа-доступа на Android 14 (`READ_MEDIA_VISUAL_USER_SELECTED`); Photo Picker без разрешений; неоднозначность `shouldShowRequestPermissionRationale`; auto-reset ~90 дней.
- **Утечки**: retention «объект собирается, но слишком поздно» как отдельный класс; механика LeakCanary (ObjectWatcher + KeyedWeakReference + 5с/GC + Shark); `DisposeOnViewTreeLifecycleDestroyed` для ComposeView в Fragment.
- **ANR**: пороги по компонентам; финальный стектрейс вводит в заблуждение; слепые зоны StrictMode.
- **Безопасность интентов**: hijacking custom scheme, App Links + assetlinks.json, обязательные флаги мутабельности PendingIntent, intent redirection.
- **ViewModel**: иерархия StoreOwner, лимит Bundle 1 МБ, что переживает process death.
- **Compose a11y + Android 16**, **AndroidView interop**, **Gradle build internals**, **App Startup** — детали ниже.

---

## Тема 1. Kotlin: система типов и конкурентность — ловушки

### Теория
Три места, где ломается «книжное» понимание. **(1) Variance.** Declaration-site (`out`/`in` на объявлении класса) фиксирует роль параметра: `out T` — только producer-позиции, `in T` — только consumer. Use-site (проекции `Array<out T>`, `Array<in T>`) ограничивает конкретное использование там, где класс инвариантен; `Array<out T>` соответствует Java `Array<? extends T>`. Star-projection `<*>` = «читать безопасно как upper bound, писать нельзя (кроме null)». **(2) `reified`** работает только в `inline`-функциях: тип встраивается в каждый call-site, снимая type erasure (`x is T`, `T::class` становятся доступны); `noinline` исключает лямбду из инлайнинга (её можно хранить/передавать), `crossinline` запрещает non-local return, но оставляет инлайнинг — нужно, когда лямбда вызывается из другого контекста (Runnable, другой поток). **(3) extension-функции диспетчеризуются статически** — по объявленному типу, не по runtime-типу: это не полиморфизм, а сахар над статическим методом с receiver-ом как первым аргументом.

### Q1.1 (GOTCHA) — `equals()` у data class с полем-массивом
**Junior.** «data class сам генерирует `equals()`/`hashCode()` по всем полям конструктора, так что два объекта с одинаковыми данными равны из коробки.»

**Middle.** «С массивом ломается: сгенерированный `equals()` для поля `Array` вызывает унаследованный от `Any` `equals()`, а это ссылочное равенство. Два инстанса с идентичным содержимым массива будут НЕ равны. Нужно вручную переопределить `equals()`/`hashCode()` через `contentEquals()`/`contentHashCode()`.»

**Senior.** «Корень в том, что `Array` не переопределяет `equals()` — в Kotlin `==` для массивов это референсное равенство (в отличие от `List`; официально: по умолчанию `equals()` наследуется от `Any` и реализует referential equality, а для сравнения элементов массивов нужен `contentEquals()`). data class генерирует `equals()`, только если он не написан явно, поэтому фикс — ручной override с `contentEquals()`/`contentHashCode()`, а для вложенных массивов `contentDeepEquals`/`contentDeepHashCode`. Критично: предупреждение об этом даёт **IDE-инспекция IntelliJ/Android Studio ‘Array property in data class’** (‘recommended to override equals() and hashCode()’), а НЕ компилятор — чистый `kotlinc`-билд соберётся молча, и на CLI-CI эту проблему легко не заметить. Практическое следствие: такой data class ломает дедупликацию в `Set`, ключи в `Map`, `distinctUntilChanged()` в Flow и `assertEquals` в тестах. По этой причине в domain-моделях предпочитают `List`/immutable-коллекции вместо массивов.»

**Зачем спрашивают.** Проверяют, понимает ли кандидат разницу между структурным и референсным равенством и знает ли, что data class не панацея. Тот, кто ловил этот баг в проде (сломанный `distinctUntilChanged`, «мигающий» UI), ответит мгновенно; книжный кандидат уверенно скажет «работает из коробки».

### Q1.2 (GOTCHA) — `synchronized` через suspension point
**Junior.** «Для защиты общего состояния оборачиваю в `synchronized`/`@Synchronized` — как в Java, потокобезопасно.»

**Middle.** «В корутинах вместо `synchronized` нужен `Mutex.withLock`, потому что `Mutex` suspend-friendly: он не блокирует поток, а приостанавливает корутину.»

**Senior.** «Держать `synchronized`/`ReentrantLock` через suspension point — это баг корректности, а не только производительности. Монитор JVM привязан к потоку: захватывает и освобождает его один и тот же поток. Но при `suspend`-вызове (`delay`, сетевой вызов) корутина может возобновиться на ДРУГОМ потоке dispatcher-а — освобождать монитор будет не тот поток, что захватил, что нарушает семантику взаимного исключения. `Mutex` привязан к корутине (логической единице исполнения), а не к потоку, поэтому suspension-safe. Критично: `Mutex` **НЕ реентрантный** — повторный `lock()` из той же корутины, что уже держит лок, приведёт к вечному подвисанию (в отличие от реентрантного `synchronized`). Trade-off: `Mutex` заметно медленнее JVM-локов на CPU-bound операциях без suspend внутри — там лучше atomic-типы или обычный lock. Идеал по официальному гайду — вообще избегать shared mutable state: immutability, single-thread confinement через dispatcher, или actor/`StateFlow`.»

**Зачем спрашивают.** Один из самых разоблачающих вопросов. Junior видит только «блокирует поток — плохо для перформанса», не понимая, что это ещё и неверно. Senior добавит про нереентрантность `Mutex` (частый источник дедлоков) и про то, когда `Mutex` — плохой выбор.

### Q1.3 — Channel vs Flow: когда Channel действительно нужен
**Junior.** «Flow для потоков данных, Channel — устаревшее, лучше всегда Flow.»

**Middle.** «Flow холодный (пересоздаётся на каждого коллектора), Channel горячий (существует независимо от получателей). Channel хорош для one-shot событий и коммуникации между корутинами: каждое значение получает ровно один получатель.»

**Senior.** «Channel уместен как примитив producer↔consumer с backpressure: capacity задаёт поведение — `RENDEZVOUS`(0) синхронизирует sender и receiver (обмен только при встрече); `BUFFERED`; `CONFLATED` (буфер 1, новое затирает старое); `UNLIMITED`. Для fan-out/fan-in Channel естественнее Flow. Ключевая ловушка — `receiveAsFlow` vs `consumeAsFlow`: `consumeAsFlow` гарантирует одного коллектора и закрывает канал при отмене сбора (защита от утечки), `receiveAsFlow` допускает несколько коллекторов, но ответственность за закрытие канала — на вас, иначе канал висит вечно = leak. В MVI из Части 1 события UI (NewsPublisher) я делаю через `Channel(BUFFERED)` + `receiveAsFlow`, а состояние — через StateFlow: события нельзя терять и не нужно реплеить последнее (в отличие от state).»

**Зачем спрашивают.** Отделяют тех, кто знает «Channel устарел, есть SharedFlow» на уровне слогана, от тех, кто понимает семантику capacity и различие consume/receive. Реальный опыт выдаёт упоминание конкретной утечки через `receiveAsFlow` без закрытия.

---

## Тема 2. Runtime permissions и модель приватности (Android 11→16)

### Теория
`shouldShowRequestPermissionRationale()` — источник главной ловушки: он возвращает `true`, только если система считает, что стоит показать объяснение (обычно после первого отказа). Комбинация «`checkSelfPermission` != GRANTED И `shouldShow...` == false» означает ЛИБО «пользователь ещё не спрашивался», ЛИБО «отказано навсегда» — API не различает эти случаи. Корректная детекция «denied forever» строится на состоянии: запоминаем в DataStore, что запрос делался; если делался, а `shouldShow` false — это permanently denied, надо вести в Settings. Версии: **11** — one-time permissions, permission auto-reset, package visibility; **12** — приблизительная геолокация, индикаторы микрофона/камеры; **13** — `POST_NOTIFICATIONS` runtime, гранулярные `READ_MEDIA_IMAGES/VIDEO/AUDIO`, `NEARBY_WIFI_DEVICES`; `READ_EXTERNAL_STORAGE` deprecated с API 33; **14** — `READ_MEDIA_VISUAL_USER_SELECTED` (частичный доступ) + обязательные foreground service types; **15/16** — ужесточение FGS и while-in-use.

### Q2.1 (GOTCHA) — версионное ветвление медиа-разрешений
**Junior.** «Прошу `READ_EXTERNAL_STORAGE` и читаю галерею через MediaStore.»

**Middle.** «`READ_EXTERNAL_STORAGE` deprecated с API 33. На 13+ прошу гранулярные `READ_MEDIA_IMAGES`/`VIDEO`, для старых версий оставляю `READ_EXTERNAL_STORAGE` с `maxSdkVersion=32`.»

**Senior.** «Матрица трёхуровневая. На Android 14+ есть третье состояние — ЧАСТИЧНЫЙ доступ: пользователь может выбрать ‘Select photos’, и тогда `READ_MEDIA_IMAGES`/`VIDEO` будут DENIED, а гранится `READ_MEDIA_VISUAL_USER_SELECTED`. Если не объявить это разрешение, система запускает compatibility-режим, где доступ временный и отзывается при уходе приложения в фон. Проверка: (1) 13+ и granted IMAGES/VIDEO → полный; (2) 14+ и granted VISUAL_USER_SELECTED → частичный, и надо дать пользователю дозабрать больше файлов, не считая библиотеку пустой; (3) ≤12 и READ_EXTERNAL_STORAGE → полный; (4) иначе denied. Ключевой архитектурный вывод: если не нужен собственный picker — вообще не запрашивать разрешения, использовать Photo Picker (`PickVisualMedia`), который не требует permission и работает на Android 11+ через модульные обновления Google Play. Собственный gallery picker оправдан только при полном контроле над UX.»

**Зачем спрашивают.** Медиа-разрешения — минное поле версий. Middle знает про гранулярность 13, но пропускает частичный доступ 14 (третье состояние!). Senior сразу предложит Photo Picker как способ убрать проблему.

### Q2.2 (GOTCHA) — «denied forever» и ActivityResultContracts
**Junior.** «Если разрешение не дано, снова запускаю `requestPermissionLauncher.launch()`.»

**Middle.** «Проверяю `shouldShowRequestPermissionRationale`: если true — показываю объяснение и запрашиваю снова; если false после отказа — веду в системные настройки.»

**Senior.** «Тонкость: `shouldShow==false` неоднозначен — это и ‘ещё не спрашивали’, и ‘отказано навсегда’. Только по нему различить нельзя. Корректно — хранить флаг ‘запрос делался’ в DataStore. Сценарий: первый запуск — `shouldShow` false, но это не permanent, launcher покажет диалог; после первого отказа — `shouldShow` true; после второго отказа (второй отказ = permanent на новых версиях) — `shouldShow` false, и повторный `launch()` **молча вернёт denied без диалога**. Именно повторный `launch()` в этом состоянии — самый частый баг: кнопка ‘Разрешить’ визуально работает, но ничего не происходит. Правильно — Intent в `ACTION_APPLICATION_DETAILS_SETTINGS`. Плюс permission auto-reset (Android 11+) отзывает runtime-разрешения приложения после ~90 дней неиспользования (порог `auto_revoke_unused_threshold_millis2` ≈ 7 776 000 000 мс = 90 дней) — состояние ‘granted’ нельзя кэшировать навсегда, проверять надо каждый раз.»

**Зачем спрашивают.** Проверяют, понимает ли кандидат, что `shouldShowRequestPermissionRationale` не является детектором permanent-denial. Тот, кто реально отлаживал «кнопка не реагирует», знает про молчаливый denied от повторного launch.

---

## Тема 3. Утечки памяти и профилирование (обновлено под Compose/Flow)

### Теория
Классика (статический Handler, синглтон с Activity-контекстом, listener без отписки, ViewModel держит View) плюс новое для стека. **Compose:** `ComposeView` внутри Fragment без правильной `ViewCompositionStrategy` держит Composition после уничтожения View — нужна `DisposeOnViewTreeLifecycleDestroyed`; `remember {}`, захватывающий Context/Activity/callback, удерживает их пока жив Composition. **Flow:** сбор горячего Flow без привязки к lifecycle; `launchWhenX` (deprecated) не отменяет сбор, а приостанавливает, оставляя upstream активным — правильно `repeatOnLifecycle(STARTED)`, отменяющий корутину сбора в STOPPED. **Scope:** `GlobalScope`, scope без отмены. Диагностика: **LeakCanary** через `ObjectWatcher` держит `KeyedWeakReference` на уничтоженные Activity/Fragment/View/ViewModel; официально: «если weak-ссылка не очистилась после ожидания 5 секунд и запуска GC, объект считается retained; порог по умолчанию — 5 retained-объектов когда приложение видимо, и 1 когда в фоне»; дампит `.hprof` и парсит через **Shark**, находя «кратчайший путь сильных ссылок от GC Roots к retained-инстансам» (leak trace).

### Q3.1 (GOTCHA) — «объект же в итоге собирается GC, значит утечки нет»
**Junior.** «Если LeakCanary не ругается и OOM нет — утечек нет, GC всё соберёт.»

**Middle.** «Утечка — когда объект удерживается ссылкой после того, как должен быть уничтожен. LeakCanary ловит retained Activity/Fragment по weak-ссылке, которая не очищается после onDestroy.»

**Senior.** «Есть категория, которую интуиция и базовая настройка LeakCanary пропускают: объект в конце концов собирается GC, но удерживается СЛИШКОМ ДОЛГО. Пример — Flow, собираемый в `launchWhenStarted`: корутина не отменяется в фоне, upstream (сеть/DB) продолжает работать, держа промежуточные объекты; при возврате на экран всё соберётся — формально ‘не leak’, но это лишние аллокации, работа CPU и трафик в фоне. LeakCanary 3 умеет ловить именно это — детекция монотонного роста объектов между повторами сценария, даже если технически они пока не ‘leaking’. Для диагностики ‘долго живущих, но не вечных’ объектов нужен Android Studio Memory Profiler: heap dump + сравнение дампов + dominators (кто удерживает больше всего retained size) + allocation tracking. Важное различие: LeakCanary смотрит СВОИ `KeyedWeakReference`, а Studio-профайлер ищет по `mFragmentManager` — отсюда расхождения (false positives у Studio для ещё не использованных фрагментов).»

**Зачем спрашивают.** Разделяет «leak = красная нотификация LeakCanary» и «retention как класс проблем перформанса». Senior понимает, что отсутствие OOM ≠ отсутствие проблемы, и знает разницу инструментов.

### Q3.2 — ComposeView в кастомной Fragment-навигации
**Junior.** «Кладу `ComposeView` в `onCreateView`, вызываю `setContent {}` — работает.»

**Middle.** «Обязательно ставлю `view.setViewCompositionStrategy(DisposeOnViewTreeLifecycleDestroyed)`, иначе Composition переживёт View Fragment-а при возврате по бэкстеку и утечёт.»

**Senior.** «В кастомной Fragment-навигации из Части 1 стратегия по умолчанию `DisposeOnDetachedFromWindowOrReleasedFromPool` НЕ подходит: у Fragment на бэкстеке View уничтожается (`onDestroyView`), но сам Fragment жив, и дефолтная стратегия может не диспознуть Composition вовремя — держатся `remember`-состояния, подписки, лямбды с захваченным Context. Нужна `DisposeOnViewTreeLifecycleDestroyed`. Второй слой: всё, что захвачено в `remember` (callback во ViewModel, Context), живёт пока жив Composition — поэтому колбэки оборачиваю в `rememberUpdatedState`, а долгоживущие ссылки не кладу в `remember`. Проверяю LeakCanary — он из коробки вотчит и ViewModel, и View Fragment-а.»

**Зачем спрашивают.** Специфично для гибридного стека (Compose внутри Fragment). Реальный опыт выдаёт знание конкретной стратегии и того, почему дефолтная течёт именно на бэкстеке.

---

## Тема 4. ANR, StrictMode и дисциплина main-thread

### Теория
ANR — это НЕ просто «заблокировал main». Разные компоненты — разные таймауты: **input dispatching** — 5 с (единственный тип, считающийся user-perceived; официально: «сейчас user-perceived считаются только ANR типа Input dispatching timed out»); **BroadcastReceiver** — 10 с (foreground) / 60 с (background); **Service** — 20 с (foreground) / 200 с (background); `startForeground()` не вызван за ~5 с; **ContentProvider** — ~10 с. Тонкость: ANR может произойти БЕЗ очевидной работы на main — при синхронном binder-вызове в другой процесс, при contention за lock, который держит другой поток, или при дедлоке. StrictMode: `ThreadPolicy` ловит disk/network на main, custom slow calls, resource mismatches; `VmPolicy` — leaked closables/cursors, leaked Activity, exposed file:// URI. Что StrictMode НЕ ловит: чистые CPU-bound вычисления на main, lock contention, jank от тяжёлой рекомпозиции. Влияние на Play: пороги плохого поведения — не менее **0,47% дневных активных пользователей** с user-perceived ANR по всем устройствам, и **не менее 8%** для одной модели устройства.

### Q4.1 (GOTCHA) — стектрейс ANR указывает на Activity, но виноват не он
**Junior.** «В ANR-трейсе `Activity.loadImage` — значит, тормозит эта Activity, её и оптимизирую.»

**Middle.** «Смотрю, что делает main в момент дампа: если тяжёлая операция — выношу в корутину на `Dispatchers.IO`.»

**Senior.** «Финальный стектрейс ANR систематически вводит в заблуждение. 5-секундный таймер input dispatching стартует только после диспетчеризации input-события. BroadcastReceiver, Service и Activity выполняются на ОДНОМ main-потоке последовательно. Классика: BroadcastReceiver крутится 4.8 с (не достигая своего порога 10 с), затем приходит touch, и Activity добивает main ещё на 0.3 с — ANR ‘input dispatching’ припишется методу Activity (`loadImage`), хотя реальный виновник — receiver. Поэтому: (1) event-based стектрейс недостаточен, нужен трейс всей длительности заморозки — Perfetto/system tracing показывает картину до момента ANR; (2) кластеры ‘nativePollOnce’/‘main thread idle’ в Play Vitals — дамп, снятый слишком поздно, их игнорируют; (3) частая невидимая причина — синхронный binder-вызов: main ждёт ответа другого процесса, своей работы ‘нет’, но ANR есть — смотреть на reply-поток. jank (пропуск кадров, бюджет ~16 мс) — это НЕ ANR: jank диагностируют через Perfetto/JankStats, ANR — через traces.txt.»

**Зачем спрашивают.** Один из лучших вопросов на senior. Middle верит стектрейсу. Senior знает, что компоненты делят main, таймер привязан к input, и умеет читать Perfetto, а не только финальный трейс.

### Q4.2 — что StrictMode пропускает
**Junior.** «Включаю `StrictMode.detectAll().penaltyLog()` — ловит всё плохое на main.»

**Middle.** «`ThreadPolicy` ловит disk/network на main, `VmPolicy` — утечки closable и Activity. Включаю только в debug, `penaltyDeath` для критичного.»

**Senior.** «StrictMode — не серебряная пуля, у него слепые зоны. Он ловит I/O и leaked-ресурсы, но НЕ ловит: чистые CPU-bound вычисления на main (парсинг большого JSON из памяти, тяжёлый цикл), lock contention, тяжёлую рекомпозицию Compose, синхронные binder-вызовы. То есть самые коварные ANR — мимо StrictMode. Ещё нюанс: `penaltyDeath` может убить приложение на легитимном lifecycle-disk-access (Google сам предупреждает ‘не чините всё подряд’). Практика: StrictMode с `penaltyListener` → отправка violation в аналитику/Crashlytics в debug/QA; для того, что он не ловит, — Perfetto и Macrobenchmark (cross-link на Baseline Profiles из Части 1). StrictMode-нарушения пробрасываются через IPC: если системный сервис лезет на диск от вашего имени, поймается.»

**Зачем спрашивают.** Проверяют границы инструмента. Middle знает API, Senior знает, чего инструмент НЕ видит — и что именно это опаснее всего.

---

## Тема 5. Deep links, App Links и безопасность интентов

### Теория
Три вещи путают. **Custom scheme** (`myapp://`) — любое приложение может зарегистрировать тот же scheme, ОС может молча отдать интент другому → hijacking (критично для OAuth-callback: кража authorization code). **Deep link** через http(s) без верификации — покажет chooser или уйдёт в браузер. **Android App Links** — verified: `android:autoVerify="true"` + `assetlinks.json` (Digital Asset Links) на `https://domain/.well-known/assetlinks.json` с SHA-256 fingerprint подписи → ОС проверяет связь домен↔пакет↔сертификат, ссылка открывается прямо в приложении без chooser. С Android 15 система периодически ре-верифицирует домены в фоне (изменения `assetlinks.json` доходят до устройств до 7 дней). Безопасность интентов: **PendingIntent mutability** обязателен с Android 12 (API 31); **`android:exported`** обязателен явно с API 31 для компонентов с intent-filter; **intent redirection** — извлечение вложенного Intent из extras и его запуск.

### Q5.1 (GOTCHA) — почему приложение падает на старте без изменения кода нотификаций
**Junior.** «`PendingIntent.getActivity(ctx, 0, intent, FLAG_UPDATE_CURRENT)` — стандартный код нотификации.»

**Middle.** «С Android 12 нужно явно указывать `FLAG_IMMUTABLE` или `FLAG_MUTABLE`, иначе `IllegalArgumentException`. Ставлю `FLAG_IMMUTABLE`, если не нужна мутабельность.»

**Senior.** «Тонкость: при targetSdk 31+ создание `PendingIntent` без флага мутабельности бросает `IllegalArgumentException` (‘Targeting S+ requires FLAG_IMMUTABLE or FLAG_MUTABLE’) — и падает не обязательно ваш код: краш часто прилетает из транзитивной зависимости (WorkManager `ForceStopRunnable`, старый push-SDK, библиотека нотификаций), поэтому ‘я не менял нотификации, а оно упало на старте после бампа targetSdk’ — типичный сценарий. Почему `FLAG_IMMUTABLE` дефолт: мутабельный PendingIntent позволяет получателю дозаполнить пустые поля базового Intent — если base Intent неявный, злоумышленник может перенаправить его (intent redirection). `FLAG_MUTABLE` оправдан только для inline-reply/bubbles/Direct Share. Смежно: `android:exported` тоже стал обязателен явно на 12+ — манифест не смёрджится без него для компонентов с intent-filter.»

**Зачем спрашивают.** Middle знает про флаг. Senior объясняет security-модель (почему immutable — дефолт, что такое redirection) и знает, что краш приходит из зависимостей — это признак реального опыта миграции targetSdk.

### Q5.2 (GOTCHA) — привязка deep link к кастомной Fragment-навигации + безопасность
**Junior.** «Ловлю deep link в manifest intent-filter, беру `intent.data`, парсю URL, открываю нужный экран.»

**Middle.** «Использую App Links с `autoVerify` вместо custom scheme, чтобы избежать hijacking. В Activity разбираю `intent.data` и через свой Router открываю нужный Fragment.»

**Senior.** «Две вещи. (1) Custom scheme принципиально hijack-able: любое приложение регистрирует тот же `myapp://`, и для OAuth-callback это кража кода — для auth ТОЛЬКО App Links (они на DNS + Digital Asset Links), плюс PKCE, плюс не передавать токены в URL, а короткоживущий opaque-код. Даже у App Links есть surface: `autoVerify=false` или битый `assetlinks.json`/неверный SHA-256 → тихий fallback на браузер/chooser, и защита теряется. (2) В кастомной навигации из Части 1: единая entry-Activity разбирает `intent.data` и делегирует Router-у, который выполняет Fragment-транзакцию. Опасная ловушка — если из deep-link-параметра извлекается сериализованный Intent и запускается (`startActivity(getParcelableExtra("next"))`): это intent redirection — внешний источник заставляет приложение запустить произвольный, в т.ч. непубличный, компонент. Валидировать: whitelisting хостов/путей (не `contains`/`startsWith`), не доверять extras, проверять, что целевой компонент — свой.»

**Зачем спрашивают.** Проверяют security-mindset + связь с архитектурой навигации. Senior знает про silent fallback App Links и intent redirection — уровень пентеста, не туториала.

---

## Тема 6. ViewModel и scoping состояния — ловушки

### Теория
`ViewModelStoreOwner` определяет время жизни. `by viewModels()` — scope текущего Fragment; `by activityViewModels()` — scope Activity (шаринг между фрагментами одной Activity); nav-graph-scoped — scope графа. **SavedStateHandle** переживает process death (не только config change), сериализуя данные в `Bundle`, который уходит через binder-транзакцию. `getStateFlow(key, default)` даёт `StateFlow`, переживающий и config change, и process death. Лимит: `Bundle`/binder ~1 МБ (`TransactionTooLargeException`), причём буфер ОБЩИЙ на все транзакции процесса — упасть можно и на суммарно меньших. Хранить только минимум для реставрации UI. Частые ошибки: «сброс» ViewModel (взят не тот owner / пересоздан store) или «утечка» (держит View/Context/подписку навсегда). CreationExtras — механизм передачи параметров в фабрику.

### Q6.1 (GOTCHA) — общий ViewModel «сбрасывается» между фрагментами
**Junior.** «Шарю ViewModel через `by activityViewModels()` — один инстанс на все фрагменты.»

**Middle.** «Если `by viewModels()` в каждом фрагменте — будут разные инстансы. Для шаринга беру `activityViewModels()` или nav-graph scope. ‘Сброс’ обычно из-за неправильного owner.»

**Senior.** «Ключевая ловушка — какой именно StoreOwner вы получаете. `activityViewModels()` привязывает к Activity — переживёт смену фрагментов, но и утечёт, если ViewModel держит ссылку на конкретный Fragment/его View. `viewModels()` в родительском Fragment ≠ в child Fragment: child-фрагменты в `FragmentContainerView` имеют свой store. Классический ‘сброс’: берут `viewModels()` в двух фрагментах, ожидая шаринг, — получают два инстанса. Другой ‘сброс’: пересоздание store owner (replace фрагмента вместо возврата по стеку) уничтожает ViewModel. В кастомной навигации из Части 1, где транзакции ручные, надо осознанно выбирать owner: для флоу из нескольких экранов — либо nav-graph-scope, либо ViewModel на родительском host-фрагменте, а дочерние берут `requireParentFragment()` как owner. Про Dagger (ручной inject из Части 1): ViewModel с параметрами создаётся через кастомную `ViewModelProvider.Factory` + `CreationExtras`, а `SavedStateHandle` — через `AbstractSavedStateViewModelFactory`/AssistedInject.»

**Зачем спрашивают.** Проверяют понимание иерархии StoreOwner-ов. Middle знает `activityViewModels`, Senior знает про child-fragment store, про replace vs backstack и как это стыкуется с ручным Dagger.

### Q6.2 (GOTCHA) — SavedStateHandle и process death
**Junior.** «`SavedStateHandle` спасает состояние при повороте экрана.»

**Middle.** «Он переживает и process death, не только config change (ViewModel сам переживает config change, но не смерть процесса). `getStateFlow` даёт StateFlow, устойчивый к обоим.»

**Senior.** «Тонкостей три. (1) Что переживает: только то, что положено В SavedStateHandle и Parcelable/сериализуемо; обычное поле ViewModel переживёт config change, но НЕ process death. (2) Лимит ~1 МБ: SavedStateHandle сериализуется в Bundle на `onStop()` и уходит через binder — большой список даёт `TransactionTooLargeException`, а поскольку буфер ОБЩИЙ на процесс, упасть может и на суммарно меньших транзакциях. Хранить надо ID/минимум для реставрации, а данные — в репозитории/Room (cross-link на Room из Части 1). (3) Тестировать это надо не поворотом, а ‘Don’t keep activities’ / убийством процесса из Studio — большинство ‘работает’ ломается именно на реальном process death. Для отладки размера Bundle — TooLargeTool.»

**Зачем спрашивают.** Middle знает лозунг «переживает process death». Senior знает лимит 1 МБ (и что буфер общий), что именно НЕ переживает, и как правильно тестировать.

---

## Тема 7. Compose accessibility и адаптив/большие экраны (Android 16)

### Теория
**Semantics tree** — параллельное дереву Composition представление для TalkBack и тестов. `mergeDescendants = true` — объединить дочерние узлы в один фокусируемый (Row с иконкой+текстом читается как одно). Ловушка: элемент, который сам мёржит (Checkbox с `onCheckedChange != null`, любой clickable), НЕ вольётся в родительский merge — надо `onCheckedChange = null`. `clearAndSetSemantics {}` — стереть семантику потомков и задать свою (для кастомных элементов списка). `contentDescription` vs `stateDescription` (последнее читается ПЕРЕД contentDescription и описывает состояние). `liveRegion` (Polite/Assertive) — автонотификация об изменениях. **Android 16 (API 36):** для приложений с targetSdk 36 на экранах sw ≥ 600dp ИГНОРИРУЮТСЯ ограничения ориентации/resizability/aspect-ratio (`screenOrientation`, `resizeableActivity=false`, `minAspectRatio`, `maxAspectRatio`) — приложение заполняет весь экран в любой ориентации. Временный opt-out через `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`, убираемый в API 37. Google Play требует targeting API 36 с 31 августа 2026 (для новых приложений и обновлений; Wear OS/Android TV — минимум API 35).

### Q7.1 (GOTCHA) — экран «работает», но недоступен TalkBack или ломается на планшете
**Junior.** «Compose сам генерирует semantics, TalkBack работает из коробки, а layout адаптивный, раз я на dp.»

**Middle.** «Для группировки ставлю `mergeDescendants`, задаю `contentDescription` на иконках. Для планшетов смотрю `WindowSizeClass` и делаю разные layout.»

**Senior.** «Два независимых провала. (1) A11y: `mergeDescendants` на Row НЕ сработает, если внутри есть самомёржащийся узел — Checkbox с активным `onCheckedChange` или clickable останутся отдельными фокусами, TalkBack прочитает разорванно. Фикс — `onCheckedChange = null` на дочернем + обработчик на контейнере, либо `clearAndSetSemantics` для полностью кастомного описания. Для кастомного тоггла мало merge — нужны `stateDescription`, `role`, `toggleableState`. Для действий в списке — `customActions` вместо кучи фокусируемых иконок (иначе навигация по элементу = много свайпов). (2) Android 16 ломает главную скрытую ассумпцию: код с `screenOrientation="portrait"` или `resizeableActivity=false` при targetSdk 36 на sw≥600dp просто игнорируется — экран, ‘работавший’ только в портрете, растянется в ландшафте: растянутые на всю ширину компоненты, кнопки за экраном без скролла, поехавшая camera preview. Смена ориентации/размера чаще пересоздаёт Activity — теряется state, если не сохранён (cross-link на SavedStateHandle, Тема 6). Google уже шлёт warning в Play Console про orientation restrictions. Правильно — адаптивные layout через WindowSizeClass, `maxWidth` на компонентах, скроллящиеся контейнеры, и НЕ полагаться на фиксированную ориентацию.»

**Зачем спрашивают.** Два senior-угла разом. Middle знает `mergeDescendants` и WindowSizeClass, но пропускает, что clickable ломает merge, и не знает про изменение Android 16 (самое актуальное на 2026). Senior свяжет ещё и потерю state при resize.

### Q7.2 — testable accessibility
**Junior.** «A11y проверяю глазами, включив TalkBack вручную.»

**Middle.** «Пишу Compose UI-тесты по семантике: `onNodeWithContentDescription`, `assertIsToggleable`. Semantics tree — то же дерево, что видит и тест, и TalkBack.»

**Senior.** «Ключевое: semantics tree — единый источник и для TalkBack, и для тестов, поэтому тестируемость и доступность растут вместе. Правильно замёрдженный узел с `stateDescription`/`role` одновременно и корректно озвучивается, и надёжно находится тестом по состоянию, а не по тексту. На 2026 использую Compose UI Check (аудит адаптивности) и `DeviceConfigurationOverride` в тестах для симуляции размеров/ориентаций — чтобы ловить регрессии Android 16 в CI, а не на устройстве. Обязательно: тесты, где узел меняет `stateDescription`, и тесты на разных WindowSizeClass.»

**Зачем спрашивают.** Проверяют, связывает ли кандидат a11y и тестируемость через один semantics tree — признак зрелого подхода, а не «прикрутим contentDescription перед релизом».

---

## Тема 8. AndroidView interop и миграция View→Compose в большом приложении

### Теория
Обратное направление к Части 1 (Compose внутри Fragment). `AndroidView(factory, update)`: **`factory` вызывается РОВНО ОДИН раз** (на UI-потоке) — создание View и one-off настройка; **`update` вызывается после factory и на каждой рекомпозиции**, когда меняется прочитанный в нём State — здесь ставят новые свойства. Главная ловушка — работа не в той лямбде: создание/подписки в `update` (пересоздание на каждой рекомпозиции), либо изменяемые свойства в `factory` (не обновятся). Есть `onReset`/`onRelease` (1.4+) для переиспользования View в Lazy-списках. `AndroidViewBinding` — для инфляции XML/встраивания Fragment. Composition locals НЕ пробрасываются автоматически через границу View↔Compose. Nested scroll interop работает, если контейнер участвует в nested-scroll протоколе. Стратегия миграции: leaf-first vs container-first.

### Q8.1 (GOTCHA) — работа в неправильной лямбде AndroidView
**Junior.** «В `factory` создаю View, настраиваю listener, ставлю данные — всё в одном месте.»

**Middle.** «Создание — в `factory` (один раз), обновление свойств от состояния — в `update` (на рекомпозиции). Listener ставлю в factory.»

**Senior.** «Точная модель: `factory` — ровно один раз (создание + константная настройка + one-off listener), `update` — после factory И на каждой рекомпозиции при изменении прочитанного State. Ловушки в обе стороны: (1) если положить в `factory` то, что зависит от меняющегося состояния (`textView.text = state.title`) — оно застынет на первом значении; (2) если в `update` создавать объекты/ставить listener заново — это происходит на КАЖДОЙ рекомпозиции: утечки, дёргающиеся анимации, повторные подписки. Ещё: лямбда-callback, читающая свежее состояние, оборачивается в `rememberUpdatedState`, иначе либо застаревает, либо форсит пересоздание. Sizing: `AndroidView` по умолчанию не всегда дружит с `WRAP_CONTENT` — часто нужен явный modifier или измерения. В Lazy-списках использую overload с `onReset`, чтобы Compose переиспользовал View при скролле. Не держу ссылку на View в `remember` вне AndroidView — только внутри factory.»

**Зачем спрашивают.** Классический interop-gotcha. Middle знает «factory раз, update много», но не проговаривает симптомы обеих ошибок и `rememberUpdatedState`. Senior добавит sizing, onReset для Lazy и запрет на remember View.

### Q8.2 — стратегия миграции большого приложения
**Junior.** «Переписываю экран целиком на Compose, экран за экраном.»

**Middle.** «Инкрементально: новые экраны на Compose, старые оставляю на XML, на стыке — `ComposeView`/`AndroidView`. Гибридные экраны неизбежны на переходный период.»

**Senior.** «На масштабе выбор leaf-first vs container-first — trade-off. Leaf-first (сначала мелкие компоненты внутри XML через `ComposeView`) безопаснее — маленькие обратимые PR, но долго и много interop-мостов. Container-first (экран-контейнер на Compose, старые куски через `AndroidView`/`AndroidViewBinding`) быстрее убирает XML-скелет, но крупные рискованные PR. На практике: новые экраны — сразу Compose; существующие критичные — container-first с постепенной заменой листьев; шаринг ViewModel в переходный период через общий StoreOwner (Тема 6), чтобы XML- и Compose-части одного флоу видели одно состояние. Composition locals не пробрасываются через границу автоматически — тему/DI-скоуп надо явно прокидывать. Nested scroll между Compose-контейнером и View работает только если оба в nested-scroll протоколе, иначе двойной скролл. Метрика решения — не ‘красивость’, а частота изменений экрана (мигрируем сначала то, что активно трогаем) и его перф/крэш-статистика.»

**Зачем спрашивают.** Проверяют системное мышление о миграции, а не механику одного AndroidView. Senior рассуждает о размере PR, шаринге ViewModel, composition locals и выборе по частоте изменений.

---

## Тема 9. Gradle build performance — внутреннее устройство

### Теория
**KAPT медленный**, потому что генерирует Java-стабы: компилятор дважды резолвит все символы (сначала для стабов, потом реальная компиляция; по kotlinlang.org генерация стабов «стоит примерно 1/3 полного kotlinc-анализа»). **KSP** работает как компиляторный плагин Kotlin, читая символы напрямую — до 2× быстрее (по Android Developers Blog: «dramatically improves build speed — up to 2x faster for Room's Kotlin test app»). На 2026 KSP2 — дефолт с начала 2025; KSP1 deprecated и несовместим с Kotlin 2.3.0+ и AGP 9.0+; KAPT в maintenance mode. **Configuration cache** сохраняет результат configuration-фазы (граф задач); ломается доступом к `Project` в execution-фазе (`Task.project` в `doLast`), чтением System property/env/файлов в configuration без `Provider`/`ValueSource`, `buildFinished`-листенерами. Gradle 9.0 (31 июля 2025) сделал CC preferred-режимом. **Convention plugins:** buildSrc (инвалидирует CC всей сборки при изменении) vs included builds vs precompiled script plugins. Что реально ускоряет: миграция на KSP, config cache, модуляризация, отказ от dynamic versions; что карго-культ: избыточное дробление, преждевременная оптимизация.

### Q9.1 (GOTCHA) — «добавлю модулей и включу всё — станет быстрее»
**Junior.** «Разобью на много модулей и включу все флаги параллелизма — сборка ускорится.»

**Middle.** «Мигрирую KAPT→KSP (главный выигрыш), включаю configuration cache и build cache, использую version catalogs. Модуляризация помогает параллелизму.»

**Senior.** «Что реально двигает стрелку и что карго-культ — разные вещи. Реально: (1) KAPT→KSP — убирает генерацию стабов и двойной резолв; (2) configuration cache; (3) осмысленная модуляризация ради параллелизма и инкрементальности; (4) отказ от dynamic versions (`+`) и SNAPSHOT — они форсят проверки сети и ломают воспроизводимость. Карго-культ: (1) чрезмерное дробление — сотни микромодулей дают overhead конфигурации, а при изменении графа только вредят configuration cache; (2) `buildSrc` для convention-плагинов — ЛЮБОЕ изменение в buildSrc инвалидирует CC для ВСЕЙ сборки; на масштабе convention-плагины лучше как отдельный included build/публикуемый артефакт, потребляемый через version catalog. Тонкость CC на масштабе: один несовместимый сторонний плагин на одном модуле ломает cache hit для всех задач этого модуля, а через convention-плагин в корне — потенциально для всей сборки. Измерять надо через `--profile`/Build Scan/Develocity, а не на глаз.»

**Зачем спрашивают.** Отделяют «слышал, что модули = быстро» от понимания, что именно и почему. Senior знает про buildSrc-инвалидацию CC и обратную сторону дробления.

### Q9.2 — что ломает configuration cache
**Junior.** «Включил `org.gradle.configuration-cache=true` — не собралось, выключил обратно.»

**Middle.** «CC ломает доступ к `Task.project` в execution-фазе. Читаю HTML-отчёт CC, чиню свои задачи, обновляю плагины.»

**Senior.** «Модель: всё, что нужно в execution, должно быть захвачено в configuration как сериализуемый снапшот (входы/выходы задачи), а не через живой `Project`. Ломают: `Task.project` в `doLast`/`doFirst`; чтение System property/env/файла в configuration напрямую (нужен `providers.systemProperty`/`ValueSource`, иначе любое изменение инвалидирует кэш — `File.exists()` в конфиге добавляет директорию во входы, и любой новый файл сбрасывает кэш); `buildFinished`-листенеры (заменять на dataflow actions/Build Service); шаринг мутабельного состояния между фазами. Тонкость отчёта: атрибуция по byte-pattern — задача, помеченная как проблемная, часто лишь держит ссылку на объект, сериализованный из upstream-плагина; идти по всей цепочке полей, реальный виновник обычно выше. На 2026 CC — preferred в Gradle 9, а Isolated Projects (следующая фича параллельной конфигурации) требует полной CC-совместимости — так что миграция сейчас = очередь за будущим ускорением.»

**Зачем спрашивают.** Middle читает отчёт и чинит. Senior понимает ПРИНЦИП (снапшот vs живой Project), знает про ValueSource, ложную атрибуцию и связь с Isolated Projects.

---

## Тема 10. App Startup и инициализация на масштабе

### Теория
Наивный подход — каждый SDK объявляет свой `ContentProvider` для авто-инициализации — бьёт по cold start: каждый ContentProvider инстанцируется ОС ПЕРЕД `Application.onCreate()`, последовательно и в недетерминированном порядке. **androidx.startup** заменяет это одним `InitializationProvider` (ContentProvider), который через `<meta-data>` в манифесте находит все `Initializer<T>` и запускает их. `Initializer.create(context)` выполняет инициализацию; `dependencies()` возвращает список Initializer-ов, от которых зависит текущий, — образуя граф, топологически сортируемый (циклы падают с RuntimeException). Eager (по умолчанию, до onCreate) vs lazy: `tools:node="remove"` на meta-data отключает авто-инициализацию, дальше `AppInitializer.getInstance(ctx).initializeComponent(...)` вручную. Multi-process: onCreate и провайдеры запускаются в КАЖДОМ процессе — нужны проверки процесса. Cross-link на cold start из Части 1.

### Q10.1 (GOTCHA) — «App Startup ускоряет старт, потому что параллелит»
**Junior.** «Подключу App Startup — он параллелит инициализацию SDK, старт ускорится.»

**Middle.** «Главный выигрыш — не параллелизм, а схлопывание N ContentProvider-ов (каждый дорог) в ОДИН InitializationProvider. Ещё можно сделать часть инициализаций lazy через `tools:node=remove` + `AppInitializer`.»

**Senior.** «Тонкость: App Startup сам по себе НЕ обязательно ускоряет — он всё ещё запускает eager-инициализаторы до `Application.onCreate()` на main. Выигрыш в двух вещах: (1) один ContentProvider вместо многих (инстанцирование каждого CP — заметная стоимость на cold start, плюс их порядок недетерминирован); (2) явный граф зависимостей вместо гонки. Но если все инициализаторы оставить eager, вы просто перенесли ту же работу под один провайдер. Реальное ускорение — АГРЕССИВНЫЙ lazy/deferred: то, что не нужно для первого кадра (аналитика, часть crash-reporting, feature-SDK), переводить в lazy и триггерить после отрисовки. Второй gotcha — multi-process: `InitializationProvider.onCreate` и `Application.onCreate` выполняются в КАЖДОМ процессе (main, `:push`, `:work`), и тяжёлые инициализаторы прогоняются по разу на процесс — без проверки имени процесса вы платите многократно и можете инициализировать ненужное в служебном процессе. Измерять — Macrobenchmark cold start + trace (cross-link на Baseline Profiles/cold start из Части 1). Наивный per-SDK ContentProvider (как у LeakCanary, WorkManager по умолчанию) хорош для библиотек, но на масштабе десятков SDK backfire-ит из-за суммы стоимостей инстанцирования.»

**Зачем спрашивают.** Middle знает про схлопывание провайдеров. Senior понимает, что eager App Startup сам не ускоряет, знает про multi-process double-init и связывает с измерением через Macrobenchmark.

### Q10.2 — порядок и граф зависимостей
**Junior.** «Инициализирую всё в `Application.onCreate()` по порядку — просто и предсказуемо.»

**Middle.** «В App Startup порядок задаётся через `dependencies()`: если A зависит от B, B инициализируется первым. Только зависимый Initializer нужно объявлять в манифесте — его зависимости подтянутся.»

**Senior.** «Граф через `dependencies()` топологически сортируется, циклы падают с RuntimeException на старте — это лучше молчаливого неверного порядка, который был у per-CP подхода (ОС инициализирует ContentProvider-ы в НЕдетерминированном порядке, поэтому межзависимости между SDK через отдельные CP в принципе ненадёжны). В манифест достаточно положить только листовой зависимый Initializer — его `dependencies()` подтянут остальные, для них meta-data не нужна. Но всё это всё ещё eager. Мой подход: критичный минимум (DI-граф, Timber-логгер из Части 1, crash-reporting) — eager через граф; всё остальное — lazy через `AppInitializer.initializeComponent()` после первого кадра/по требованию. Так граф даёт корректность порядка, а lazy — быстрый cold start. `Application.onCreate()` для всего плох тем, что это последовательный main-thread bottleneck без управления порядком и без lazy.»

**Зачем спрашивают.** Проверяют, понимает ли кандидат ценность явного графа (детерминизм + детект циклов) против недетерминизма CP, и умеет ли комбинировать eager-граф с lazy. Реальный опыт — упоминание, что в манифесте нужен только листовой Initializer.

---

## Практические задачи (6 gotcha-ловушек)

Каждая — короткий сниппет с комментарием: что не так и какую тему раскрывает.

### Задача 1 — data class с массивом: сломанный equals
```kotlin
data class Frame(val id: Int, val pixels: ByteArray)

fun test() {
    val a = Frame(1, byteArrayOf(1, 2, 3))
    val b = Frame(1, byteArrayOf(1, 2, 3))
    println(a == b)        // печатает false!
    val set = setOf(a, b)  // size == 2, а не 1
}
// ФИКС: ручной override
class FrameFixed(val id: Int, val pixels: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FrameFixed) return false
        return id == other.id && pixels.contentEquals(other.pixels)
    }
    override fun hashCode(): Int = 31 * id + pixels.contentHashCode()
}
```
**Ловушка/цель:** сгенерированный `equals()` сравнивает `ByteArray` по ссылке (`Any.equals`), не по содержимому. Раскрывает **Тему 1** (структурное vs референсное равенство; предупреждает только IDE-инспекция, не компилятор).

### Задача 2 — synchronized/lock через suspension point
```kotlin
class Counter {
    private var value = 0
    private val lock = java.util.concurrent.locks.ReentrantLock()

    // БАГ: lock удерживается через suspend-вызов — корутина возобновится,
    // возможно, на другом потоке dispatcher-а; unlock сделает не тот поток
    suspend fun incrementBad() {
        lock.lock()
        try { value++; delay(10) } finally { lock.unlock() }
    }

    // ПРАВИЛЬНО: Mutex привязан к корутине, а не к потоку
    private val mutex = Mutex()
    suspend fun incrementSafe() = mutex.withLock {
        value++
        delay(10)            // безопасно
        // ВНИМАНИЕ: mutex НЕ реентрантный — повторный withLock изнутри = дедлок
    }
}
```
**Ловушка/цель:** монитор/JVM-lock привязан к потоку, а корутина меняет поток при возобновлении. `Mutex` suspension-safe, но НЕ реентрантный. Раскрывает **Тему 1**.

### Задача 3 — Flow без lifecycle-awareness
```kotlin
// БАГ: сбор не отменяется в фоне — upstream (GPS/сеть) работает вечно
viewLifecycleOwner.lifecycleScope.launch {
    viewModel.locationUpdates.collect { updateMap(it) } // горячий Flow, не завершается
}
// launchWhenStarted (deprecated) тоже плох — только приостанавливает, не отменяет

// ПРАВИЛЬНО:
viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.locationUpdates.collect { updateMap(it) } // отменяется в STOPPED
    }
}
```
**Ловушка/цель:** `launch`/`launchWhenStarted` оставляют upstream активным в фоне (retention, трафик, батарея) — не всегда OOM, но всегда лишняя работа. Раскрывает **Тему 3** и cross-link на Flow из Части 1.

### Задача 4 — PendingIntent без флага мутабельности
```kotlin
// БАГ: краш IllegalArgumentException при targetSdk 31+ (часто из транзитивной зависимости)
val pi = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

// ПРАВИЛЬНО:
val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
else PendingIntent.FLAG_UPDATE_CURRENT
val pi2 = PendingIntent.getActivity(ctx, 0, intent, flags)
```
**Ловушка/цель:** с API 31 обязателен `FLAG_IMMUTABLE`/`FLAG_MUTABLE`, иначе краш. `FLAG_MUTABLE` + неявный Intent = intent redirection. Раскрывает **Тему 5**.

### Задача 5 — AndroidView: работа в неправильной лямбде
```kotlin
// БАГ: данные в factory застынут; listener в update пересоздаётся на каждой рекомпозиции
@Composable
fun BadChart(state: ChartState) {
    AndroidView(
        factory = { ctx -> ChartView(ctx).apply { data = state.points } }, // застынет
        update = { view -> view.setOnClickListener { /* новый listener каждый раз */ } }
    )
}
// ПРАВИЛЬНО: создание+listener в factory (один раз), данные в update
@Composable
fun GoodChart(state: ChartState, onClick: () -> Unit) {
    val currentOnClick by rememberUpdatedState(onClick)
    AndroidView(
        factory = { ctx -> ChartView(ctx).apply { setOnClickListener { currentOnClick() } } },
        update = { view -> view.data = state.points }
    )
}
```
**Ловушка/цель:** `factory` — один раз, `update` — на каждой рекомпозиции; путаница лямбд даёт застывший UI или пересоздание/утечки. Раскрывает **Тему 8**.

### Задача 6 — ViewModel в неправильном scope
```kotlin
// БАГ: оба фрагмента ждут общий VM, но получают РАЗНЫЕ инстансы
class FragmentA : Fragment() { private val vm: FlowVM by viewModels() }
class FragmentB : Fragment() { private val vm: FlowVM by viewModels() }

// ПРАВИЛЬНО для шаринга в рамках флоу (host-фрагмент как owner):
class ChildFragment : Fragment() {
    private val vm: FlowVM by viewModels(
        ownerProducer = { requireParentFragment() }
    )
}
```
**Ловушка/цель:** `viewModels()` даёт scope самого фрагмента → два инстанса вместо общего; выбор StoreOwner определяет шаринг и время жизни. Раскрывает **Тему 6** и cross-link на кастомную навигацию из Части 1.

---

## Recommendations
- **Для самоподготовки к собеседованию**: по каждой теме уметь произнести именно senior-вариант gotcha-вопроса и назвать конкретный симптом из прода (сломанный `distinctUntilChanged` от массива; «кнопка Разрешить не реагирует» от повторного `launch()`; ANR-стектрейс на Activity, но виноват receiver; краш PendingIntent из зависимости после бампа targetSdk). Если можете назвать симптом — это сигнал реального опыта, а не заучивания.
- **Приоритет-1 темы на 2026** (самые вероятные и самые разоблачающие): Android 16 orientation/resizability (Тема 7), permission-версии и denied-forever (Тема 2), PendingIntent/App Links security (Тема 5), `synchronized` через suspension point (Тема 1). Начните с них.
- **Практический аудит своего проекта перед собеседованием**: (1) прогоните `grep` на `data class` с `Array`/`ByteArray`; (2) проверьте `ComposeView` в фрагментах на `DisposeOnViewTreeLifecycleDestroyed`; (3) прогоните LeakCanary + Memory Profiler на подозрительных экранах; (4) включите configuration cache и прочитайте отчёт; (5) измерьте cold start Macrobenchmark-ом до/после переноса init в lazy.
- **Пороги, меняющие решения**: если targetSdk уже 36 — Android 16 orientation-изменения УЖЕ действуют на планшетах, аудит адаптивности обязателен; если Play-дедлайн (targeting API 36 с 31 августа 2026) близко — это блокер релиза, не «потом»; если ANR-rate приближается к 0,47% дневных активных пользователей — приложение теряет discoverability, нужен Perfetto-анализ, а не доверие стектрейсу; если сборка > нескольких минут на конфигурации — CC/KSP-миграция окупается.

## Caveats
- **О предупреждении для массива в data class**: официальная страница kotlinlang.org про data classes НЕ упоминает массивы; предупреждение — это IDE-инспекция IntelliJ/Android Studio «Array property in data class», а не ошибка/warning компилятора. Механизм референсного равенства подтверждён официальной страницей про equality и stdlib `contentEquals`.
- **Тайминги ANR по компонентам** (10/60 c для broadcast, 20/200 c для service) — это дефолты; официальная документация в ряде мест даёт диапазоны (broadcast foreground 10–20 c, background 60–120 c), OEM могут отличаться. Пороги Play (0,47% / 8%) актуальны на момент написания и Google их периодически пересматривает.
- **Android 16/17**: изменения ориентации/resizability для API 36 имеют временный opt-out (`PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`), но в API 37 (Android 17) opt-out убирается — это подтверждённая дорожная карта, но точные даты Play-требования для API 37 (ожидается ~август 2027) могут измениться.
- **Permission auto-reset ~90 дней** — значение из `device_config` (`auto_revoke_unused_threshold_millis2` ≈ 7,776 млрд мс); Google описывает это как «несколько месяцев» и может менять порог; на Android 12+ добавляется app hibernation поверх auto-reset.
- **assetlinks.json пропагация до 7 дней** и фоновая ре-верификация относятся к Android 15+; на Android 14 и ниже верификация обычно происходит только при установке/обновлении.
- Ряд второстепенных деталей (симптомы багов, конкретные обёртки в коде) опирается на инженерные блоги и Stack Overflow-подобные источники; фундаментальные механизмы (variance, dispatch, LeakCanary, App Startup, configuration cache, permission-версии, Android 16) подтверждены официальной документацией Android/Kotlin/Gradle.