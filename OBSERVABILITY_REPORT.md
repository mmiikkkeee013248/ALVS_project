# Отчёт по реализации Observability

## Введение и цели

Данный отчёт описывает реализацию системы наблюдаемости (Observability) для веб-приложения управления контактами на Flask. Целью работы было создание полноценной системы мониторинга, включающей сбор метрик, логирование и визуализацию данных для обеспечения прозрачности работы приложения и быстрого выявления проблем.

### Основные задачи:
- Настройка сбора метрик через Prometheus
- Визуализация метрик и логов в Grafana
- Сбор и агрегация логов через Loki и Promtail
- Создание автоматически разворачиваемых дашбордов
- Документирование всей системы

## Реализованные компоненты

### 1. Prometheus

**Назначение:** Система сбора и хранения метрик в формате временных рядов.

**Конфигурация:** `prometheus.yml`

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'flask-app'
    static_configs:
      - targets: ['webapp:5000']
    metrics_path: '/metrics'
    scrape_interval: 10s
```

**Особенности:**
- Pull-модель сбора данных (Prometheus опрашивает приложение каждые 10 секунд)
- Хранение данных во встроенной TSDB (Time Series Database)
- Веб-интерфейс доступен на порту 9090
- Интеграция с Grafana для визуализации

**Доступ:** http://localhost:9090

### 2. Grafana

**Назначение:** Платформа для визуализации метрик и логов.

**Конфигурация:**
- Автоматическая настройка источников данных через provisioning
- Автоматическое создание дашбордов при запуске
- Логин/пароль по умолчанию: `admin/admin`

**Источники данных:**
- Prometheus (http://prometheus:9090)
- Loki (http://loki:3100)

**Доступ:** http://localhost:3000

### 3. Loki

**Назначение:** Система агрегации и хранения логов.

**Особенности:**
- Оптимизирована для работы с логами
- Интеграция с Grafana для визуализации
- Эффективное хранение за счёт индексации по меткам

**Доступ:** http://localhost:3100

### 4. Promtail

**Назначение:** Агент для сбора логов и отправки их в Loki.

**Конфигурация:** `promtail.yml`

```yaml
server:
  http_listen_port: 9080
  grpc_listen_port: 0

positions:
  filename: /tmp/positions.yaml

clients:
  - url: http://loki:3100/loki/api/v1/push

scrape_configs:
  - job_name: flask-app
    static_configs:
      - targets:
          - localhost
        labels:
          job: flask-app
          __path__: /var/log/app.log
```

**Особенности:**
- Автоматический сбор логов из файла `app.log`
- Отправка логов в Loki с меткой `job: flask-app`
- Отслеживание позиции чтения для предотвращения потери данных

## Настроенные метрики

Приложение экспортирует следующие метрики на эндпоинте `/metrics`:

### 1. `http_requests_total` (Counter)

**Тип:** Counter (счётчик)

**Описание:** Общее количество HTTP запросов к приложению.

**Метки (labels):**
- `method` - HTTP метод (GET, POST)
- `path` - путь запроса (/, /add, /edit/<id>, /delete/<id>)
- `status` - HTTP статус код (200, 400, 500)

**Пример значения:**
```
http_requests_total{method="GET", path="/", status="200"} 15423
```

**Использование:**
- Подсчёт общего количества запросов
- Анализ распределения запросов по методам и путям
- Мониторинг ошибок (статусы 4xx, 5xx)

### 2. `http_request_duration_seconds` (Histogram)

**Тип:** Histogram (гистограмма)

**Описание:** Распределение времени выполнения HTTP запросов.

**Метки (labels):**
- `method` - HTTP метод
- `path` - путь запроса

**Бакеты (buckets):** [0.01, 0.05, 0.1, 0.5, 1, 2, 5] секунд

**Автоматически создаваемые метрики:**
- `http_request_duration_seconds_bucket{le="0.01"}` - количество запросов ≤ 0.01 сек
- `http_request_duration_seconds_bucket{le="0.05"}` - количество запросов ≤ 0.05 сек
- ...
- `http_request_duration_seconds_bucket{le="+Inf"}` - общее количество запросов
- `http_request_duration_seconds_count` - общее количество наблюдений
- `http_request_duration_seconds_sum` - сумма всех значений

**Использование:**
- Расчёт перцентилей (50, 95, 99)
- Анализ производительности
- Выявление медленных запросов

### 3. `http_response_time_seconds` (Gauge)

**Тип:** Gauge (измеритель)

**Описание:** Последнее время отклика для каждого комбинации method/path.

**Метки (labels):**
- `method` - HTTP метод
- `path` - путь запроса

**Пример значения:**
```
http_response_time_seconds{method="GET", path="/"} 0.023
```

**Использование:**
- Мониторинг текущего времени отклика
- Отслеживание изменений производительности в реальном времени

### Реализация сбора метрик

Метрики собираются автоматически через middleware в `web_app.py`:

```python
@app.after_request
def after_request(response):
    if request.path == '/metrics':
        return response
    
    duration = time.time() - request.start_time
    method = request.method
    path = request.path
    status = str(response.status_code)
    
    # Обновляем метрики
    http_requests_total.labels(method=method, path=path, status=status).inc()
    http_request_duration_seconds.labels(method=method, path=path).observe(duration)
    http_response_time_seconds.labels(method=method, path=path).set(duration)
    
    return response
```

## Настроенное логирование

### Структура логирования

Логирование реализовано в модуле `logger.py` с использованием стандартной библиотеки Python `logging`.

**Особенности:**
- Ротация файлов логов (максимум 5 МБ, 3 резервные копии)
- Запись в файл `app.log` и вывод в консоль
- Формат: `%(asctime)s - %(name)s - %(levelname)s - %(message)s`
- Кодировка: UTF-8

### Уровни логирования

- **INFO** - информационные сообщения (успешные операции)
- **ERROR** - ошибки при выполнении операций

### Логируемые события

1. **Загрузка контактов:**
   ```
   INFO - Загружено контактов: 5
   ```

2. **Добавление контакта:**
   ```
   INFO - Добавлен контакт: name='Иван Иванов', email='ivan@example.com'
   ```

3. **Обновление контакта:**
   ```
   INFO - Обновлён контакт: id=1, name='Петр Петров', email='petr@example.com'
   ```

4. **Удаление контакта:**
   ```
   INFO - Удалён контакт: id=1
   ```

5. **Ошибки:**
   ```
   ERROR - Ошибка при загрузке контактов: connection refused
   ERROR - Ошибка при добавлении контакта: duplicate key value violates unique constraint
   ```

### Сбор логов

Логи автоматически собираются Promtail из файла `app.log` и отправляются в Loki с меткой `job: flask-app`. В Grafana логи доступны через запрос:

```
{job="flask-app"}
```

## Дашборды Grafana

### Автоматическое provisioning

Дашборды автоматически создаются при запуске Docker контейнеров благодаря provisioning конфигурации:

**Файл:** `grafana/provisioning/dashboards/dashboard.yml`

```yaml
apiVersion: 1

providers:
  - name: 'Flask App Dashboards'
    orgId: 1
    folder: ''
    type: file
    disableDeletion: false
    updateIntervalSeconds: 10
    allowUiUpdates: true
    options:
      path: /etc/grafana/provisioning/dashboards
      foldersFromFilesStructure: true
```

### Дашборд "Flask App Observability Dashboard"

**Файл:** `grafana/dashboards/flask-app-observability.json`

Дашборд содержит три панели:

#### Панель 1: HTTP Запросы к главной странице

**Тип:** Time series (временной ряд)

**Запрос PromQL:**
```promql
http_requests_total{path="/", status="200"}
```

**Описание:** График показывает количество успешных HTTP запросов (статус 200) к главной странице приложения во времени.

**Использование:**
- Мониторинг нагрузки на приложение
- Выявление пиков активности
- Анализ трендов использования

#### Панель 2: Гистограмма времени отклика (Heatmap)

**Тип:** Bar gauge с форматом Heatmap

**Запрос PromQL:**
```promql
rate(http_request_duration_seconds_bucket{method="GET", path="/"}[5m])
```

**Легенда:** `{{le}}` (отображает верхнюю границу бакета)

**Описание:** Визуализация распределения времени отклика запросов в виде тепловой карты. Показывает, сколько запросов попадает в каждый временной интервал (бакет).

**Бакеты:**
- ≤ 0.01 секунды
- ≤ 0.05 секунды
- ≤ 0.1 секунды
- ≤ 0.5 секунды
- ≤ 1 секунда
- ≤ 2 секунды
- ≤ 5 секунд
- > 5 секунд

**Использование:**
- Анализ производительности
- Выявление медленных запросов
- Оптимизация времени отклика

#### Панель 3: Логи приложения Flask

**Тип:** Logs (логи)

**Источник данных:** Loki

**Запрос:**
```
{job="flask-app"}
```

**Описание:** Панель отображает логи приложения в реальном времени. Показывает все события: успешные операции, ошибки, информационные сообщения.

**Особенности:**
- Автоматическое обновление
- Фильтрация по времени
- Детальный просмотр записей
- Подсветка уровней логирования

**Использование:**
- Отладка проблем
- Мониторинг операций
- Анализ ошибок
- Аудит действий пользователей

## Архитектура системы Observability

```
┌─────────────────┐
│  Flask App      │
│  (web_app.py)   │
│                 │
│  ┌───────────┐  │
│  │ Метрики   │──┼──► /metrics endpoint
│  └───────────┘  │
│                 │
│  ┌───────────┐  │
│  │ Логи      │──┼──► app.log
│  └───────────┘  │
└─────────────────┘
         │
         │ (scrape каждые 10s)
         ▼
┌─────────────────┐
│  Prometheus     │
│  (порт 9090)    │
│                 │
│  - Сбор метрик  │
│  - Хранение TSDB│
│  - PromQL API   │
└─────────────────┘
         │
         │ (query)
         ▼
┌─────────────────┐
│  Grafana        │
│  (порт 3000)    │
│                 │
│  - Визуализация │
│  - Дашборды     │
│  - Алерты       │
└─────────────────┘
         ▲
         │
┌────────┴────────┐
│  Promtail        │
│  (порт 9080)    │
│                 │
│  - Чтение логов │
│  - Отправка     │
└─────────────────┘
         │
         │ (read app.log)
         ▼
┌─────────────────┐
│  Loki           │
│  (порт 3100)    │
│                 │
│  - Хранение     │
│  - Индексация   │
└─────────────────┘
         │
         │ (query logs)
         ▼
┌─────────────────┐
│  Grafana        │
│  (панель логов) │
└─────────────────┘
```

### Поток данных

1. **Метрики:**
   - Flask приложение экспортирует метрики на `/metrics`
   - Prometheus опрашивает эндпоинт каждые 10 секунд
   - Данные сохраняются в TSDB
   - Grafana запрашивает метрики через PromQL

2. **Логи:**
   - Flask приложение пишет логи в `app.log`
   - Promtail читает файл и отслеживает позицию
   - Логи отправляются в Loki с метками
   - Grafana запрашивает логи через LogQL

## Примеры использования PromQL

### Базовые запросы

**Общее количество запросов:**
```promql
http_requests_total
```

**Количество запросов по методам:**
```promql
sum by (method) (http_requests_total)
```

**Количество запросов по путям:**
```promql
sum by (path) (http_requests_total)
```

**Количество ошибок (статусы 4xx и 5xx):**
```promql
sum(http_requests_total{status=~"4..|5.."})
```

### Анализ производительности

**Средняя скорость запросов за 5 минут:**
```promql
rate(http_requests_total[5m])
```

**Среднее время отклика:**
```promql
rate(http_request_duration_seconds_sum[5m]) / rate(http_request_duration_seconds_count[5m])
```

**50-й перцентиль (медиана) времени отклика:**
```promql
histogram_quantile(0.50, rate(http_request_duration_seconds_bucket[5m]))
```

**95-й перцентиль времени отклика:**
```promql
histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m]))
```

**99-й перцентиль времени отклика:**
```promql
histogram_quantile(0.99, rate(http_request_duration_seconds_bucket[5m]))
```

### Фильтрация и группировка

**Запросы к конкретному пути:**
```promql
http_requests_total{path="/"}
```

**Успешные запросы:**
```promql
http_requests_total{status="200"}
```

**Запросы по методам и путям:**
```promql
sum by (method, path) (http_requests_total)
```

**Текущее время отклика для GET запросов:**
```promql
http_response_time_seconds{method="GET"}
```

## Развёртывание

### Запуск через Docker Compose

```bash
docker-compose up -d
```

### Проверка работы компонентов

1. **Prometheus:** http://localhost:9090
   - Проверить targets: http://localhost:9090/targets
   - Выполнить запрос: `http_requests_total`

2. **Grafana:** http://localhost:3000
   - Логин: `admin`
   - Пароль: `admin`
   - Дашборд "Flask App Observability Dashboard" должен быть доступен автоматически

3. **Loki:** http://localhost:3100
   - Проверить готовность: http://localhost:3100/ready

4. **Promtail:** Проверить логи контейнера
   ```bash
   docker logs promtail
   ```

### Генерация тестовых данных

Для проверки работы системы можно выполнить несколько запросов:

```bash
# Запросы к главной странице
curl http://localhost:5000

# Добавление контакта через веб-интерфейс
# Откройте http://localhost:5000 в браузере и добавьте контакт

# Проверка метрик
curl http://localhost:5000/metrics
```

## Заключение

В рамках реализации системы Observability для Flask приложения была создана полноценная инфраструктура мониторинга, включающая:

✅ **Сбор метрик** через Prometheus с тремя типами метрик (Counter, Histogram, Gauge)  
✅ **Сбор логов** через Loki и Promtail с автоматической ротацией  
✅ **Визуализация** в Grafana с автоматически разворачиваемыми дашбордами  
✅ **Автоматизация** развёртывания через Docker Compose  
✅ **Документация** всех компонентов и процессов  

Система обеспечивает:
- **Прозрачность** работы приложения через метрики и логи
- **Быстрое выявление проблем** через визуализацию и алерты
- **Анализ производительности** через гистограммы и перцентили
- **Удобство использования** через автоматическое развёртывание дашбордов

Все компоненты интегрированы и готовы к использованию в production окружении.

## Полезные ссылки

- [Prometheus Documentation](https://prometheus.io/docs/)
- [Grafana Documentation](https://grafana.com/docs/)
- [Grafana Loki Documentation](https://grafana.com/docs/loki/latest/)
- [Prometheus Client Python](https://github.com/prometheus/client_python)
