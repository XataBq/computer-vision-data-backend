# Backend видеоданных — полный MVP

Java backend предоставляет CV-модулю JPEG-кадры и HLS из RTSP/HLS-камер и загруженных MP4. Четыре сценария:

1. Реальная камера → текущий JPEG snapshot.
2. Реальная камера → live HLS с H.264 и MPEG-TS сегментами.
3. Загруженный MP4 → JPEG на заданной секунде.
4. Загруженный MP4 → зацикленный HLS с чтением в реальном времени (`-re -stream_loop -1`).

Снимки выдаются в исходном разрешении, без resize, фиксированного FPS или inference. Проект независим от Python/YOLO: существующий CV-код можно размещать рядом с `backend`.

## Стек и структура

Java 21, Spring Boot 3.5.13, Maven, Spring MVC, JPA, PostgreSQL 16, Flyway, Validation, Actuator, springdoc 2.8.17, FFmpeg/FFprobe.

```text
src/main/java/com/example/media/
  camera/       Camera, repository, service, REST API, DTO
  video/        Video, repository, service, REST API, DTO
  media/        ProcessExecutor, FFmpeg, FFprobe, metadata
  stream/       ProcessManager, source stream service, HLS media API
  storage/      StorageService, локальные оригиналы, временное HLS-хранилище
  common/       timestamps/UUID, проверка и маскирование URL
  config/       типизированные configuration properties
  exception/    единый JSON-формат ошибок
src/main/resources/db/migration/
  V1__create_cameras_and_videos.sql
src/test/java/com/example/media/
  unit, MockMvc и PostgreSQL Testcontainers tests
```

Модульный монолит, package-by-feature. `Camera` и `Video` — отдельные JPA-сущности. `BaseEntity` содержит только UUID и временные метки, не представляет общую сущность источника. Контроллеры не собирают команды и не работают с файлами. `ProcessExecutor` обслуживает короткие snapshot/probe операции, `ProcessManager` управляет долгоживущими потоками, `HlsCommandFactory` формирует HLS-команды. Оба режима используют общую политику входа `MediaInputArguments`. Для ROI позже добавляется этап обработки в media-слое, без изменения REST-контроллеров.

```text
                         CV
                         ▲
                         │
                    JPEG / HLS
                         │
                ┌────────┴────────┐
                │  Spring Boot    │
                │ Media Services  │
                └────────┬────────┘
                         │
              ┌──────────┴──────────┐
              │                     │
           FFmpeg                FFprobe
              │
       ┌──────┴──────┐
       │             │
    Camera          Video
   RTSP/HLS          MP4
```

Схема PostgreSQL остаётся прежней: `cameras` (UUID PK, name, source_type, source_url, status, created_at, updated_at) и `videos` (UUID PK, original_filename, unique storage_path, mime_type, size_bytes, duration_seconds, width, height, fps, codec, status, timestamps). Новая миграция не нужна: live process state и HLS output намеренно не сохраняются в БД. Ограничения и типы столбцов заданы в `V1__create_cameras_and_videos.sql`.

## Требования

- Для запуска в контейнерах: Docker Engine/Docker Desktop и Docker Compose.
- Для локальной сборки: JDK 21 и Maven 3.6.3+.
- Для локального запуска: PostgreSQL и FFmpeg/FFprobe в PATH либо явные пути в переменных окружения. FFmpeg должен поддерживать RTSP, HTTP(S), HLS и MJPEG.
- Java 17 из системного PATH недостаточно; укажите JDK 21 через `JAVA_HOME`.

## Запуск в Docker

Выполняйте команды из `backend`:

```powershell
Copy-Item .env.example .env
docker compose up --build -d
docker compose logs -f backend
```

Compose поднимает PostgreSQL и backend с FFmpeg/FFprobe без дополнительных профилей. Backend работает от непривилегированного пользователя. Порты доступны на localhost. Данные БД и оригиналы в `/app/data/uploads` сохраняются в именованных volumes. Текущий volume `/app/data` также содержит streams, но они остаются временными и очищаются при stop/exit/restart конкретного потока. Compose проверяет health приложения и даёт 30 секунд на graceful shutdown. При увеличении `HLS_MAX_STREAMS` или `HLS_STOP_TIMEOUT` соответственно увеличьте `stop_grace_period`. `docker compose down` сохраняет данные; флаг `-v` удаляет volumes.

Только PostgreSQL:

```powershell
docker compose up -d postgres
```

## Локальная сборка и запуск

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn.cmd -DskipTests package
java -jar target/media-backend-0.0.1-SNAPSHOT.jar
```

На Linux/macOS используйте `mvn` вместо `mvn.cmd`. `-DskipTests` компилирует тестовые исходники, но не запускает тесты. В Dockerfile используется этот же режим. Альтернатива запуску JAR: `mvn.cmd spring-boot:run`.

Flyway применяет миграцию при запуске. Hibernate использует `ddl-auto=validate` и не создаёт/обновляет схему самостоятельно. PostgreSQL должен быть доступен до запуска приложения. `MediaAvailability` один раз выполняет `ffmpeg -version` и `ffprobe -version`: отсутствие или неисправность бинарника останавливает startup с понятным сообщением. Результат хранится для Actuator (`mediaTools`); каждый health request не создаёт новые процессы. Наличие encoder `libx264` требуется для HLS и дополнительно проверяется реальным media integration test.

## Конфигурация

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/media` | JDBC URL при локальном запуске |
| `POSTGRES_DB` | `media` | Имя БД в Compose |
| `POSTGRES_USER` | `media` | Пользователь PostgreSQL |
| `POSTGRES_PASSWORD` | `media` | Пароль для локальной разработки |
| `POSTGRES_PORT` | `5432` | Порт PostgreSQL на хосте в Compose |
| `PORT` | `8080` | Порт приложения / опубликованный порт Compose |
| `STORAGE_ROOT` | `./data` | Корень файлового хранилища; `/app/data` в контейнере |
| `FFMPEG_PATH` | `ffmpeg` | Путь к FFmpeg для локального запуска |
| `FFPROBE_PATH` | `ffprobe` | Путь к FFprobe для локального запуска |
| `SNAPSHOT_TIMEOUT` | `15s` | Таймаут извлечения кадра |
| `PROBE_TIMEOUT` | `20s` | Таймаут анализа видео |
| `MEDIA_MAX_CONCURRENT_PROCESSES` | `4` | Предел коротких snapshot/probe процессов |
| `RTSP_TRANSPORT` | `tcp` | Общий транспорт RTSP для snapshot и HLS (`tcp` / `udp`) |
| `HLS_SEGMENT_DURATION` | `2` | Целевая длительность MPEG-TS сегмента, секунды (1–30) |
| `HLS_PLAYLIST_SIZE` | `5` | Число сегментов в sliding playlist (2–100) |
| `HLS_STARTUP_TIMEOUT` | `10s` | Ожидание playlist и первого готового сегмента |
| `HLS_STOP_TIMEOUT` | `3s` | Ожидание graceful stop, затем столько же на forced stop |
| `HLS_MAX_STREAMS` | `4` | Общий предел долгоживущих потоков обоих типов (1–64) |
| `UPLOAD_MAX_FILE_SIZE` | `500MB` | Максимальный размер файла |
| `UPLOAD_MAX_REQUEST_SIZE` | `501MB` | Лимит multipart-запроса с запасом на служебные поля |

Snapshot/probe таймауты должны быть положительными и не превышать 10 минут; HLS startup/stop — 2 минуты. При исчерпании любого лимита процессов запрос получает 503, без неограниченной очереди. HLS defaults: H.264 (`libx264`, `veryfast`), без audio, без принудительного изменения FPS/разрешения, 2-секундные сегменты и окно из 5 сегментов.

`.env` загружается Docker Compose, но не автоматически JVM. Для локального запуска задайте переменные в оболочке. Если меняете имя БД или опубликованный порт PostgreSQL, обновите локальный `DB_URL`. Настройки путей FFmpeg/FFprobe должны указывать на исполняемый файл, а не строку с дополнительными аргументами.

## API

| Метод | Путь | Результат |
|---|---|---|
| POST | `/api/cameras` | Создание камеры, 201 + Location |
| GET | `/api/cameras` | Список камер |
| GET | `/api/cameras/{id}` | Камера, либо 404 |
| DELETE | `/api/cameras/{id}` | Удаление, 204 |
| GET | `/api/cameras/{id}/snapshot` | Текущий JPEG, 200 |
| POST | `/api/cameras/{id}/hls/start` | Запуск HLS / существующая RUNNING session, 200 |
| POST | `/api/cameras/{id}/hls/stop` | Идемпотентный stop и cleanup, 200 |
| GET | `/api/cameras/{id}/hls/status` | Статус процесса и streamUrl |
| POST | `/api/videos` | Multipart, поле `file`; видео и metadata, 201 |
| GET | `/api/videos` | Список видео |
| GET | `/api/videos/{id}` | Metadata, либо 404 |
| DELETE | `/api/videos/{id}` | Удаление файла и записи, 204 |
| GET | `/api/videos/{id}/snapshot?second=N` | JPEG из видео, 200 |
| POST | `/api/videos/{id}/simulation/start` | Запуск зацикленного real-time HLS, 200 |
| POST | `/api/videos/{id}/simulation/stop` | Идемпотентный stop и cleanup, 200 |
| GET | `/api/videos/{id}/simulation/status` | Статус процесса и streamUrl |
| GET | `/streams/cameras/{id}/index.m3u8` | Playlist камеры, `application/vnd.apple.mpegurl` |
| GET | `/streams/cameras/{id}/segment_000001.ts` | Сегмент камеры, `video/mp2t` |
| GET | `/streams/videos/{id}/index.m3u8` | Playlist симуляции |
| GET | `/streams/videos/{id}/segment_000001.ts` | Сегмент симуляции |

Swagger UI: <http://localhost:8080/swagger-ui/index.html>. OpenAPI JSON: <http://localhost:8080/v3/api-docs>. Health: <http://localhost:8080/actuator/health>. Остальные Actuator endpoints не опубликованы.

Ниже примеры для PowerShell (`curl.exe`, чтобы не использовать alias Invoke-WebRequest):

```powershell
# Создание камеры через JSON-файл; содержимое — UTF-8.
'{"name":"Parking entrance","sourceType":"RTSP","sourceUrl":"rtsp://user:password@host:554/stream"}' | Set-Content -Encoding utf8 camera.json
curl.exe -X POST http://localhost:8080/api/cameras -H "Content-Type: application/json" --data-binary "@camera.json"

# Загрузка MP4
curl.exe -X POST http://localhost:8080/api/videos -F "file=@parking_test.mp4;type=video/mp4"

# Подставьте UUID из ответа API
curl.exe --fail-with-body http://localhost:8080/api/cameras/CAMERA_UUID/snapshot --output camera.jpg
curl.exe --fail-with-body "http://localhost:8080/api/videos/VIDEO_UUID/snapshot?second=30" --output video.jpg

# Все управляющие endpoints камеры
curl.exe -X POST http://localhost:8080/api/cameras/CAMERA_UUID/hls/start
curl.exe http://localhost:8080/api/cameras/CAMERA_UUID/hls/status
curl.exe -X POST http://localhost:8080/api/cameras/CAMERA_UUID/hls/stop

# Все управляющие endpoints симуляции
curl.exe -X POST http://localhost:8080/api/videos/VIDEO_UUID/simulation/start
curl.exe http://localhost:8080/api/videos/VIDEO_UUID/simulation/status
ffplay http://localhost:8080/streams/videos/VIDEO_UUID/index.m3u8
curl.exe -X POST http://localhost:8080/api/videos/VIDEO_UUID/simulation/stop
```

Откройте `ffplay` после успешного start и до stop. Аналогично камера доступна через `ffplay http://localhost:8080/streams/cameras/CAMERA_UUID/index.m3u8`. Swagger группирует endpoints как Cameras, Videos, Camera Streams, Video Simulations, Snapshots и HLS Media.

Start возвращает `sourceId`, `type` (`CAMERA`/`VIDEO_SIMULATION`), `status: RUNNING`, `running: true`, относительный `streamUrl` и `startedAt`. Такой ответ выдаётся только после появления валидного playlist и непустого сегмента при живом процессе. Повторный start возвращает ту же сессию без второго FFmpeg. При раннем exit/таймауте — 503 и cleanup. Stop/status без живого процесса возвращают `status: STOPPED`, `running: false`, `streamUrl: null`, `startedAt: null`. Для несуществующего источника управляющие endpoints возвращают 404.

Для HLS-камеры используйте `sourceType: HLS` и HTTP/HTTPS URL. RTSP принимает только `rtsp://`. Произвольные схемы и отсутствующий host отклоняются. Доступность камеры проверяется при snapshot, не при регистрации. Статус `ACTIVE` означает зарегистрированную включённую камеру; мониторинг доступности и автоматические переходы в `UNAVAILABLE` пока отсутствуют.

Snapshot возвращает `image/jpeg` и `Cache-Control: no-store`. Параметр `second` обязателен; допустимы дробные значения в диапазоне `0 <= second <= durationSeconds`, NaN/Infinity запрещены. Для `second == durationSeconds` используется момент на один средний интервал кадра раньше, поскольку граница длительности не содержит кадр. Для VFR или повреждённых временных меток это приближение; если кадр не извлечён, возвращается 503.

Пример ошибки:

```json
{
  "timestamp": "2026-09-25T12:00:00Z",
  "status": 404,
  "code": "CAMERA_NOT_FOUND",
  "message": "Camera not found",
  "path": "/api/cameras/00000000-0000-0000-0000-000000000000"
}
```

400 — неверный запрос/время; 404 — источник не найден; 413 — превышение upload limit; 415 — неподдерживаемый контейнер или Content-Type запроса; 422 — недопустимое видео/нет видеопотока; 503 — недоступный источник, таймаут, занятый media executor или недоступный FFprobe; 500 — внутренняя ошибка/ошибка хранилища. Ошибки snapshot также возвращаются JSON. Сообщения процессов, stack traces и rejected values клиенту не передаются.

## Хранилище и медиапроцессы

```text
data/
  uploads/videos/<videoId>.mp4             # постоянный оригинал
  streams/cameras/<cameraId>/index.m3u8    # временный HLS
  streams/cameras/<cameraId>/segment_*.ts
  streams/videos/<videoId>/index.m3u8      # временная simulation
  streams/videos/<videoId>/segment_*.ts
```

`ProcessManager` хранит `ConcurrentHashMap<StreamKey, ManagedProcess>`: Process, sourceId/type, startedAt, output directory. Команда и URL не сохраняются в сессии и не выдаются клиенту. Операции start/stop/delete одного источника синхронизированы до commit БД, поэтому start не может вклиниться между stop и delete. Число объектов блокировок фиксировано. Для разных источников операции могут выполняться параллельно.

Stop сначала вызывает `destroy()`, ждёт ограниченное время и при необходимости вызывает `destroyForcibly()`. `onExit()` удаляет только соответствующую генерацию процесса и её файлы; старый callback не может удалить перезапущенную сессию. `@PreDestroy` запрещает новые start и останавливает все процессы. При невозможности подтвердить остановку delete источника не продолжается. Stdout/stderr долгоживущего FFmpeg перенаправлены в OS discard, без заполнения pipes и утечки credentials.

Каждый start очищает только каталог своего типа и UUID. Stop, штатный shutdown и самостоятельный exit также удаляют временный output. `delete_segments` ограничивает рост сегментов; несколько недавно удалённых из playlist сегментов могут кратковременно оставаться на диске по правилам FFmpeg. `temp_file` публикует только готовые сегменты через rename. Принудительные ключевые кадры с периодом сегмента и closed GOP позволяют резать HLS без фиксированного FPS. Playlist является sliding live playlist, не VOD. Обоснование опций: [FFmpeg HLS muxer](https://ffmpeg.org/ffmpeg-formats.html#hls-2), [FFmpeg input rate / stream loop](https://ffmpeg.org/ffmpeg-doc.html).

`HlsMediaController` открывает только `index.m3u8` и `segment_<digits>.ts` в type/UUID-каталоге. `.tmp`, исходные MP4, произвольные имена и traversal не публикуются; symlink-компоненты запрещены. Для playlist/segments выставляются правильный Content-Type, `Cache-Control: no-store` и `X-Content-Type-Options: nosniff`.

Файл хранится по пути `STORAGE_ROOT/uploads/videos/<UUID>.mp4`. Исходное имя используется только как metadata, из него удаляется путь. В БД хранится внутренний ключ файла; абсолютные пути не выдаются через API. Локальные `data`, `target` и `.env` исключены из Git.

Расширение `.mp4` проверяется, затем FFprobe анализирует фактический контейнер, MP4 brand, наличие видеопотока, codec, положительную длительность, размеры и FPS. MIME-type клиента не является доказательством корректности. MOV/3GP и audio-only MP4 отклоняются. FPS `30000/1001` преобразуется в дробное число; при неизвестном `avg_frame_rate` используется `r_frame_rate`. Файлы без достоверной положительной длительности/FPS не поддерживаются в MVP. FFprobe проверяет metadata, а не полностью декодирует весь файл: повреждение отдельных кадров может обнаружиться позднее при snapshot.

При ошибке анализа или сохранения/commit БД загруженный файл удаляется. READY-запись создаётся только после успешного анализа. DELETE камеры сначала останавливает HLS и очищает segments, затем удаляет запись. DELETE видео сначала останавливает simulation и очищает segments, затем удаляет оригинал и запись; ошибка хранилища сохраняет запись. Файловая система и PostgreSQL не образуют единой транзакции: при сбое DB commit после удаления оригинала может остаться запись без файла; повторный DELETE завершает очистку. Аварийное завершение процесса во время upload может оставить временный/непривязанный файл. Фоновая сверка хранилища не входит в MVP.

FFmpeg вызывается через `ProcessBuilder` со списком аргументов, без shell. Для каждого snapshot создаётся уникальный временный JPEG, удаляемый в `finally`. После таймаута процесс и дочерние процессы принудительно завершаются. Stdout ограничен и постоянно вычитывается; stderr отбрасывается, поскольку может содержать credentials. JPEG проверяется на наличие начального/конечного маркера и ограничивается 64 MiB в памяти. Постоянное хранение snapshot отсутствует.

URL камер хранится в БД как задано пользователем. В ответах credentials маскируются, query string скрывается целиком; в логах используются ID и безопасные коды ошибок. Не включайте SQL bind logging/HTTP request body logging для этого приложения.

## Границы MVP

- HLS имеет один вариант качества, MPEG-TS сегменты и H.264 без audio. Задержка зависит от источника, длительности сегмента, декодирования и клиента; это не протокол ultra-low-latency.
- ROI запланирован после MVP; resize, фиксированный FPS, CV/YOLO inference отсутствуют.
- Нет authentication/authorization, пагинации и автоматического мониторинга камер. Предполагается доверенная локальная сеть и контролируемый доступ к API.
- Камеры могут обращаться к внутренним адресам, что необходимо для RTSP в LAN. Проверка схем и FFmpeg protocol whitelist не является полной защитой от SSRF через HTTP redirects/HLS playlists. Ограничение сетевого доступа задаётся окружением развёртывания.
- Один экземпляр backend, локальное хранилище. Одновременное удаление и snapshot одного видео может завершить snapshot ошибкой 503. Активный HLS/simulation при delete останавливается автоматически.
- Live process state не переживает рестарт; авто-restart после сбоя источника отсутствует. Status отражает живой процесс, а не непрерывную проверку свежести кадров. После аварийного kill приложения могут остаться старые HLS-файлы, очищаемые при следующем start/stop этого источника. `SIGKILL`/жёсткое завершение ОС не позволяет выполнить `@PreDestroy`.
- S3/MinIO, очереди, GPU, микросервисы и Kubernetes не добавлены.

## Future architecture / TODO

Переход остаётся за пределами текущего монолита: `LocalStorageService → S3StorageService`, `InMemory ProcessManager → Redis/distributed jobs`, `FFmpeg in application → separate media worker`. При таком переходе worker получает задачу через очередь; оригинал скачивается/передаётся worker, а HLS публикуется в S3/MinIO/CDN. Локальные `Path` в media boundary потребуется заменить worker-local resolver; это изолированное изменение media/storage, а не новая JPA-иерархия источников.

ROI/cropping и multiple ROI per camera добавляются как optional transform перед JPEG encode и HLS encode. Сложная пустая система pipeline сейчас не создаётся. Дальнейшие TODO: S3/MinIO, separate media workers, Redis registry, task queue, automatic frame sampling, dataset generation, CV result storage, bounding boxes, occupancy history, WebSocket events, authentication/authorization, ONVIF, MJPEG, WebRTC, adaptive bitrate HLS, GPU transcoding.

## Тесты и проверка

JUnit 5 / Mockito проверяют CameraService, VideoService, URL validation, FFprobe parsing, FFmpeg command/cleanup, storage, startup validation, ProcessManager (start/duplicate/concurrent start/stop/repeated stop/timeout/early exit/stale callback/shutdown/capacity) и source stream service. MockMvc покрывает прежние endpoints, все шесть новых управляющих endpoints и фактическую раздачу HLS-файлов. PostgreSQL Testcontainers проверяет Flyway, запуск контекста и JPA mappings; media availability в этом DB-тесте замокана, поэтому локальные FFmpeg ему не нужны. UI-тестов нет.

Команды для повторной проверки:

```powershell
mvn.cmd clean test
mvn.cmd clean package
# Только интеграционная проверка (нужен работающий Docker):
mvn.cmd -Dtest=PostgresIntegrationTest test
# Реальные FFmpeg/FFprobe, без Docker или внешней камеры:
mvn.cmd -Pmedia-integration -Dtest=RealMediaIntegrationTest test
# Весь набор вместе с реальными медиа:
mvn.cmd -Pmedia-integration clean verify
```

Без Docker интеграционный тест пропускается благодаря `disabledWithoutDocker`; это не подтверждает корректность миграции на работающей БД.

Тег `media-integration` исключён из стандартного прогона, чтобы CI не требовал FFmpeg. При явном включении профиля отсутствие FFmpeg/FFprobe приводит к ошибке, а не молчаливому skip. `RealMediaIntegrationTest` создаёт двухсекундный MP4 с audio, проверяет upload/metadata, два JPEG, looping и ограниченное окно HLS, H.264 без audio, выдачу playlist/segments, идемпотентность, остановку и удаление активного видео. Локальный HTTP-сервер подаёт настоящий HLS в ветку камеры: дополнительно проверяются camera snapshot и нормализация HLS. Все запущенные процессы завершаются в `finally`. Только persistence/transaction manager в этом тесте замоканы; это не замена PostgreSQL integration test.

Результаты проверки второй части (25.09.2026):

- `mvn clean test` на Java 21: **74 passed, 0 failures, 0 errors, 0 skipped**.
- `mvn clean package`: **74 passed, 0 failures, 0 errors, 0 skipped**, исполняемый Spring Boot JAR создан.
- Docker Desktop был запущен для проверки; `PostgresIntegrationTest` прошёл на PostgreSQL 16 с применением и валидацией Flyway, проверкой JPA mappings и запуском Spring Boot.
- `mvn -Pmedia-integration -Dtest=RealMediaIntegrationTest test`: **1 passed**, реальные FFmpeg/FFprobe, HTTP HLS input и все четыре медиасценария. Этот opt-in тест не входит в число 74 стандартного набора.
- `docker compose up -d --build` — успешно; backend/PostgreSQL healthy, Flyway V1 применена, startup validation обоих бинарников успешна. Для устранения HTTP 503 при сборке официальные Ubuntu repositories переведены на HTTPS с ограниченными retries.
- `scripts/Smoke-Media.ps1` — успешно против реального Docker backend/PostgreSQL: health UP, Swagger HTTP 200, все шесть управляющих endpoints в OpenAPI, upload/metadata, JPEG на 0-й секунде и в середине, simulation HLS, JPEG/HLS камеры на локальном HTTP-источнике, корректные MIME-типы, H.264 без audio, повторные start/stop. После cleanup в БД нет тестовых Camera/Video, в uploads/streams нет тестовых файлов, в backend-контейнере остался только Java-процесс.
- Физическая RTSP/IP-камера не подключалась: RTSP URL/transport/command покрыты unit-тестами; реальная camera media-ветка проверена на локальном HLS-источнике. Длительные нагрузочные испытания не проводились.
- UI-тесты не создавались и не запускались.

Для воспроизводимого smoke test работающего приложения (с настоящей БД, без mocks):

```powershell
# Backend и PostgreSQL уже должны быть запущены; локальные ffmpeg/ffprobe/curl.exe доступны в PATH.
.\scripts\Smoke-Media.ps1
```

Скрипт проверяет health, Swagger/OpenAPI, создаёт MP4, загружает его через HTTP, получает JPEG на 0-й секунде и в середине, запускает simulation, запрашивает playlist/segments и проверяет H.264 без audio. Затем создаёт HLS-камеру с sourceUrl, доступным из backend, проверяет camera JPEG/HLS и все stop/status endpoints. В `finally` удаляются только созданные скриптом источники и временные файлы. При нестандартном размещении задайте `-BaseUrl` (URL API для клиента) и `-CameraSourceBaseUrl` (URL этого же API, доступный из контейнера backend).

Совместимость версий: [Spring Boot 3.5 requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [springdoc для Boot 3](https://springdoc.org/v2/).
