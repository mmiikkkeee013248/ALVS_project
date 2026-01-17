# Публикация образа на Docker Hub

Это руководство описывает процесс публикации Docker образа приложения ALVS на Docker Hub.

## Содержание

- [Создание аккаунта на Docker Hub](#создание-аккаунта-на-docker-hub)
- [Создание Access Token](#создание-access-token)
- [Ручная публикация](#ручная-публикация)
- [Автоматическая публикация через CI/CD](#автоматическая-публикация-через-cicd)
- [Использование опубликованного образа](#использование-опубликованного-образа)

---

## Создание аккаунта на Docker Hub

1. Перейдите на https://hub.docker.com
2. Нажмите "Sign Up" и создайте аккаунт
3. Подтвердите email адрес

---

## Создание Access Token

Для автоматической публикации через CI/CD или скрипты нужен Access Token:

1. Войдите в Docker Hub
2. Перейдите в **Account Settings** → **Security**
3. Нажмите **New Access Token**
4. Введите описание (например, "GitHub Actions CI/CD")
5. Выберите права доступа: **Read & Write** (или **Read, Write & Delete**)
6. Скопируйте созданный токен (он показывается только один раз!)

**Важно:** Сохраните токен в безопасном месте. Если потеряете, создайте новый.

---

## Ручная публикация

### Вариант 1: Использование скрипта (рекомендуется)

Скрипт автоматизирует процесс сборки и публикации:

```bash
# Сделать скрипт исполняемым
chmod +x scripts/publish-dockerhub.sh

# Авторизация в Docker Hub
docker login

# Публикация с указанием версии и username
./scripts/publish-dockerhub.sh 1.0.0 myusername

# Или использовать переменные окружения
export DOCKERHUB_USERNAME=myusername
export DOCKERHUB_TOKEN=mytoken
./scripts/publish-dockerhub.sh latest
```

**Параметры скрипта:**
- `version` (опционально) - версия образа (по умолчанию: `latest`)
- `username` (опционально) - имя пользователя Docker Hub (можно задать через `DOCKERHUB_USERNAME`)

**Что делает скрипт:**
1. Проверяет наличие Docker
2. Проверяет авторизацию в Docker Hub
3. Собирает образ с тегами `version` и `latest`
4. Публикует оба тега на Docker Hub
5. Выводит инструкции по использованию

### Вариант 2: Ручная сборка и публикация

```bash
# 1. Авторизация
docker login

# 2. Переход в корень проекта
cd /path/to/ALVS_project

# 3. Сборка образа
docker build \
  -f config/docker/Dockerfile \
  -t your_username/alvs-project:latest \
  -t your_username/alvs-project:1.0.0 \
  .

# 4. Публикация образа
docker push your_username/alvs-project:latest
docker push your_username/alvs-project:1.0.0
```

### Проверка публикации

После публикации проверьте образ на Docker Hub:

1. Откройте https://hub.docker.com/r/your_username/alvs-project
2. Убедитесь, что образ доступен
3. Проверьте теги (Tags)

Или через командную строку:

```bash
# Проверка локально
docker images | grep alvs-project

# Проверка на Docker Hub (требует авторизации)
docker pull your_username/alvs-project:latest
```

---

## Автоматическая публикация через CI/CD

Проект настроен на автоматическую публикацию через GitHub Actions.

### Настройка GitHub Secrets

1. Перейдите в ваш репозиторий на GitHub
2. Откройте **Settings** → **Secrets and variables** → **Actions**
3. Нажмите **New repository secret**
4. Добавьте два секрета:

   **Секрет 1:**
   - Name: `DOCKERHUB_USERNAME`
   - Value: ваш логин Docker Hub

   **Секрет 2:**
   - Name: `DOCKERHUB_TOKEN`
   - Value: Access Token, созданный ранее

### Как это работает

При каждом push в ветку `main`:

1. GitHub Actions запускает workflow (`.github/workflows/ci.yml`)
2. Выполняются тесты и линтер
3. Если тесты прошли, собирается Docker образ
4. Образ публикуется на Docker Hub с тегами:
   - `your_username/alvs-project:latest`
   - `your_username/alvs-project:main-<commit-sha>`

### Проверка автоматической публикации

1. Сделайте push в ветку `main`:
   ```bash
   git push origin main
   ```

2. Проверьте статус workflow:
   - Откройте вкладку **Actions** в репозитории GitHub
   - Найдите последний запуск workflow
   - Убедитесь, что все шаги выполнены успешно

3. Проверьте образ на Docker Hub:
   - Откройте https://hub.docker.com/r/your_username/alvs-project
   - Должны появиться новые теги

---

## Использование опубликованного образа

### Скачивание образа

```bash
docker pull your_username/alvs-project:latest
```

### Запуск контейнера

#### Базовый запуск

```bash
docker run -d \
  --name flask-app \
  -p 5000:5000 \
  -e PG_HOST=postgres_host \
  -e PG_PORT=5432 \
  -e PG_DB=test_db \
  -e PG_USER=postgres \
  -e PG_PASSWORD=your_password \
  your_username/alvs-project:latest
```

#### С внешней PostgreSQL

```bash
docker run -d \
  --name flask-app \
  -p 5000:5000 \
  -e PG_HOST=144.31.87.154 \
  -e PG_PORT=5432 \
  -e PG_DB=test_db \
  -e PG_USER=postgres \
  -e PG_PASSWORD=password \
  your_username/alvs-project:latest
```

#### С Docker Compose

Используйте готовый файл конфигурации:

```bash
# Установите переменную окружения с вашим Docker Hub username
export DOCKERHUB_USERNAME=your_username

# Запуск с образом из Docker Hub
cd config/docker
docker compose -f docker-compose.prod.hub.yml up -d
```

Или создайте свой `docker-compose.yml`:

```yaml
version: '3.9'

services:
  webapp:
    image: your_username/alvs-project:latest
    container_name: flask-app
    environment:
      PG_HOST: postgres
      PG_PORT: 5432
      PG_DB: ${PG_DB:-test_db}
      PG_USER: ${PG_USER:-postgres}
      PG_PASSWORD: ${PG_PASSWORD:-postgres}
    ports:
      - "5000:5000"
    depends_on:
      - postgres
    restart: unless-stopped

  postgres:
    image: postgres:16-alpine
    container_name: flask-postgres
    environment:
      POSTGRES_DB: ${PG_DB:-test_db}
      POSTGRES_USER: ${PG_USER:-postgres}
      POSTGRES_PASSWORD: ${PG_PASSWORD:-postgres}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    restart: unless-stopped

volumes:
  postgres_data:
```

Запуск:

```bash
docker-compose up -d
```

### Проверка работоспособности

```bash
# Проверка логов
docker logs flask-app

# Проверка доступности
curl http://localhost:5000

# Проверка метрик
curl http://localhost:5000/metrics
```

---

## Переменные окружения

Приложение использует следующие переменные окружения:

| Переменная | Описание | Значение по умолчанию |
|-----------|----------|----------------------|
| `PG_HOST` | Хост PostgreSQL | - |
| `PG_PORT` | Порт PostgreSQL | `5432` |
| `PG_DB` | Имя базы данных | `test_db` |
| `PG_USER` | Пользователь БД | `postgres` |
| `PG_PASSWORD` | Пароль БД | - |
| `LOG_DIR` | Директория для логов | `/app/logs` |

---

## Устранение проблем

### Ошибка: "unauthorized: authentication required"

**Причина:** Вы не авторизованы в Docker Hub.

**Решение:**
```bash
docker login
```

### Ошибка: "denied: requested access to the resource is denied"

**Причина:** Неправильное имя образа или нет прав на репозиторий.

**Решение:**
- Убедитесь, что используете правильный формат: `username/repository:tag`
- Проверьте, что у вас есть права на запись в репозиторий

### Ошибка при сборке образа

**Причина:** Проблемы с Dockerfile или зависимостями.

**Решение:**
1. Проверьте логи сборки: `docker build ... 2>&1 | tee build.log`
2. Убедитесь, что все файлы на месте
3. Проверьте `.dockerignore` - возможно, нужные файлы исключены

### Образ не обновляется на Docker Hub

**Причина:** Docker использует кэш.

**Решение:**
```bash
# Сборка без кэша
docker build --no-cache -f config/docker/Dockerfile -t your_username/alvs-project:latest .

# Или принудительная публикация
docker push your_username/alvs-project:latest --force
```

---

## Лучшие практики

1. **Версионирование:** Используйте семантическое версионирование (1.0.0, 1.1.0, etc.)
2. **Теги:** Всегда публикуйте с тегом `latest` и конкретной версией
3. **Безопасность:** Никогда не коммитьте токены в репозиторий
4. **Оптимизация:** Используйте multi-stage builds (уже реализовано)
5. **Документация:** Обновляйте README на Docker Hub с инструкциями

---

## Полезные ссылки

- [Docker Hub](https://hub.docker.com)
- [Docker Documentation](https://docs.docker.com/)
- [Docker Hub Documentation](https://docs.docker.com/docker-hub/)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
