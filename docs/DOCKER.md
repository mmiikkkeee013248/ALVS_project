# Инструкции по работе с Docker

## Задание 1: Развертывание контейнера с приложением

### Локальная сборка и запуск

1. **Сборка образа:**
   ```bash
   docker build -t alvs-project:latest .
   ```

2. **Проверка образа:**
   ```bash
   docker images | grep alvs-project
   ```

3. **Запуск контейнера с PostgreSQL:**
   ```bash
   # Запустить PostgreSQL
   docker run -d --name postgres \
     -e POSTGRES_DB=test_db \
     -e POSTGRES_USER=postgres \
     -e POSTGRES_PASSWORD=postgres \
     -p 5432:5432 \
     postgres:16-alpine

   # Запустить приложение
   docker run -d --name flask-app \
     --link postgres:postgres \
     -e PG_HOST=postgres \
     -e PG_PORT=5432 \
     -e PG_DB=test_db \
     -e PG_USER=postgres \
     -e PG_PASSWORD=postgres \
     -p 5000:5000 \
     alvs-project:latest
   ```

4. **Проверка работоспособности:**
   ```bash
   # Проверить логи
   docker logs flask-app

   # Проверить доступность
   curl http://localhost:5000

   # Проверить метрики
   curl http://localhost:5000/metrics
   ```

### Использование Docker Compose

```bash
# Запуск всех сервисов
docker-compose up -d

# Просмотр логов
docker-compose logs -f webapp

# Остановка
docker-compose down

# Пересборка после изменений
docker-compose up -d --build
```

## Задание 2: Публикация образа в DockerHub

### Настройка GitHub Secrets

1. Создайте аккаунт на DockerHub (если нет): https://hub.docker.com

2. Создайте Access Token:
   - Зайдите в DockerHub → Account Settings → Security
   - Нажмите "New Access Token"
   - Скопируйте созданный токен

3. Добавьте секреты в GitHub:
   - Перейдите в ваш репозиторий → Settings → Secrets and variables → Actions
   - Добавьте секреты:
     - `DOCKERHUB_USERNAME` = ваш логин DockerHub
     - `DOCKERHUB_TOKEN` = созданный токен

### Автоматическая публикация

После настройки секретов, при каждом push в ветку `main`:
1. CI/CD автоматически соберёт Docker образ
2. Опубликует его в DockerHub с тегами:
   - `ваш_username/alvs-project:latest`
   - `ваш_username/alvs-project:main-<sha>`

### Локальная проверка образа из DockerHub

```bash
# Скачать образ
docker pull ваш_username/alvs-project:latest

# Запустить
docker run -d --name flask-app \
  -e PG_HOST=postgres_host \
  -e PG_DB=test_db \
  -e PG_USER=postgres \
  -e PG_PASSWORD=password \
  -p 5000:5000 \
  ваш_username/alvs-project:latest

# Проверить работоспособность
curl http://localhost:5000
```

## Задание 3: Docker Compose с мониторингом

### Запуск полного стека

```bash
# Запуск всех сервисов (приложение + БД + мониторинг)
docker-compose up -d

# Проверка статуса
docker-compose ps

# Просмотр логов
docker-compose logs -f
```

### Доступ к сервисам

- **Flask приложение**: http://localhost:5000
- **Grafana**: http://localhost:3000 (логин: admin, пароль: admin)
- **Prometheus**: http://localhost:9090
- **PostgreSQL**: localhost:5432

### Настройка Grafana

1. Зайдите в Grafana: http://localhost:3000
2. Логин: `admin`, пароль: `admin`
3. Data Sources уже настроены автоматически:
   - Prometheus: http://prometheus:9090
   - Loki: http://loki:3100

### Создание дашборда в Grafana

1. **Метрики Prometheus:**
   - Create → Dashboard → Add visualization
   - Data source: Prometheus
   - Запросы:
     - `http_requests_total` - общее количество запросов
     - `rate(http_requests_total[5m])` - скорость запросов
     - `histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m]))` - 95-й перцентиль времени отклика

2. **Логи Loki:**
   - Create → Dashboard → Add visualization
   - Visualization type: Logs
   - Data source: Loki
   - Query: `{job="flask-app"}`

### Проверка работы мониторинга

1. Выполните несколько запросов к приложению:
   ```bash
   curl http://localhost:5000
   # Добавьте контакты через веб-интерфейс
   ```

2. Проверьте метрики в Prometheus:
   - Откройте http://localhost:9090
   - Введите запрос: `http_requests_total`
   - Нажмите Execute

3. Проверьте логи в Grafana:
   - Откройте созданный дашборд с логами
   - Убедитесь, что логи отображаются

## Полезные команды Docker

```bash
# Просмотр запущенных контейнеров
docker ps

# Просмотр всех контейнеров
docker ps -a

# Просмотр логов
docker logs flask-app
docker logs -f flask-app  # с follow

# Вход в контейнер
docker exec -it flask-app /bin/bash

# Остановка контейнера
docker stop flask-app

# Удаление контейнера
docker rm flask-app

# Удаление образа
docker rmi alvs-project:latest

# Очистка неиспользуемых ресурсов
docker system prune -a
```

## Структура Docker файлов

- `Dockerfile` - инструкции для сборки образа приложения
- `.dockerignore` - файлы, исключаемые из образа
- `docker-compose.yml` - оркестрация всех сервисов
- `prometheus.yml` - конфигурация Prometheus
- `promtail.yml` - конфигурация Promtail
- `grafana/provisioning/` - автоматическая настройка Grafana

## Оптимизация образа

Dockerfile использует многоступенчатую сборку (multi-stage build) для:
- Минимизации размера финального образа
- Отделения зависимостей сборки от runtime зависимостей
- Использования непривилегированного пользователя для безопасности
