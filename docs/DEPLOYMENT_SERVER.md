# Развертывание на сервере с ограниченным местом

Данная документация описывает процесс развертывания ALVS_project на сервере с использованием существующих утилит для экономии дискового пространства.

## Требования к серверу

### Обязательные требования

- **Docker** версии 20.10 или выше
- **Docker Compose** версии 1.29 или выше (или Docker Compose V2)
- **PostgreSQL** версии 12 или выше (установленный на хосте)
- **Git** для клонирования репозитория
- Минимум **500 MB** свободного места на диске (для образа приложения)
- **SSH доступ** к серверу

### Рекомендуемые требования

- **Python 3.10+** (для возможной установки зависимостей вне Docker)
- **curl** для проверки доступности приложения
- Минимум **1 GB** свободного места для комфортной работы

## Предварительная проверка сервера

Перед развертыванием выполните следующие команды для проверки окружения:

### 1. Проверка Docker

```bash
# Проверка версии Docker
docker --version

# Проверка версии Docker Compose
docker-compose --version
# или для Docker Compose V2:
docker compose version

# Проверка работоспособности Docker
docker ps
```

### 2. Проверка PostgreSQL

```bash
# Проверка версии PostgreSQL
psql --version

# Проверка статуса сервиса PostgreSQL
systemctl status postgresql
# или
systemctl status postgres

# Проверка доступности PostgreSQL
psql -U postgres -c "SELECT version();"
```

Если PostgreSQL не установлен, установите его:

```bash
# Ubuntu/Debian
sudo apt-get update
sudo apt-get install postgresql postgresql-contrib

# CentOS/RHEL
sudo yum install postgresql-server postgresql-contrib
sudo postgresql-setup initdb
sudo systemctl enable postgresql
sudo systemctl start postgresql
```

### 3. Проверка дискового пространства

```bash
# Проверка свободного места
df -h

# Проверка размера Docker
docker system df
```

### 4. Проверка сетевых настроек

```bash
# Проверка доступности портов
netstat -tuln | grep -E ':(5000|5432)'

# Проверка IP адреса сервера
hostname -I
ip addr show
```

## Настройка PostgreSQL

### Создание базы данных и пользователя

```bash
# Вход в PostgreSQL
sudo -u postgres psql

# Создание базы данных
CREATE DATABASE test_db;

# Создание пользователя (если нужно)
CREATE USER alvs_user WITH PASSWORD 'your_secure_password';

# Выдача прав
GRANT ALL PRIVILEGES ON DATABASE test_db TO alvs_user;

# Выход
\q
```

### Настройка доступа PostgreSQL для Docker

Для доступа к PostgreSQL из Docker контейнера нужно настроить `pg_hba.conf`:

```bash
# Редактирование конфигурации
sudo nano /etc/postgresql/*/main/pg_hba.conf

# Добавьте строку для доступа из Docker сети:
# host    all             all             172.17.0.0/16           md5

# Или для доступа с любого IP (менее безопасно):
# host    all             all             0.0.0.0/0               md5

# Перезапуск PostgreSQL
sudo systemctl restart postgresql
```

Также убедитесь, что PostgreSQL слушает на всех интерфейсах:

```bash
# Редактирование postgresql.conf
sudo nano /etc/postgresql/*/main/postgresql.conf

# Найдите и измените:
# listen_addresses = '*'  # вместо 'localhost'

# Перезапуск PostgreSQL
sudo systemctl restart postgresql
```

## Варианты развертывания

### Вариант A: Использование образа из Docker Hub (рекомендуется для экономии места)

Если образ уже опубликован на Docker Hub, можно использовать его без клонирования репозитория:

```bash
# 1. Создайте директорию для конфигурации
mkdir -p ~/alvs-deploy
cd ~/alvs-deploy

# 2. Создайте .env файл
cat > .env << EOF
PG_HOST=postgres
PG_PORT=5432
PG_DB=test_db
PG_USER=postgres
PG_PASSWORD=your_secure_password
DOCKERHUB_USERNAME=your_username
EOF

# 3. Скачайте docker-compose.prod.hub.yml из репозитория
# Или создайте его вручную (см. config/docker/docker-compose.prod.hub.yml)

# 4. Установите переменную окружения
export DOCKERHUB_USERNAME=your_username

# 5. Запустите контейнеры
docker compose -f docker-compose.prod.hub.yml up -d
```

**Преимущества:**
- Не нужно клонировать весь репозиторий
- Экономия дискового пространства
- Быстрое развертывание
- Всегда актуальная версия из Docker Hub

**Недостатки:**
- Требуется доступ к Docker Hub
- Нужно знать username на Docker Hub

### Вариант B: Локальная сборка из репозитория

Если нужно собрать образ локально или внести изменения:

## Клонирование репозитория

Перед развертыванием необходимо клонировать репозиторий на сервер.

### Варианты клонирования

**Вариант 1: HTTPS (рекомендуется для серверов без SSH ключей)**

```bash
# Для публичного репозитория
git clone https://github.com/mmiikkkeee013248/ALVS_project.git

# Для приватного репозитория (с Personal Access Token)
git clone https://YOUR_TOKEN@github.com/mmiikkkeee013248/ALVS_project.git
```

**Вариант 2: SSH (если настроены SSH ключи)**

```bash
git clone git@github.com:mmiikkkeee013248/ALVS_project.git
```

### Создание Personal Access Token (для приватных репозиториев)

Если репозиторий приватный и у вас нет SSH ключей:

1. Перейдите в GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Создайте новый токен с правами `repo`
3. Используйте токен в URL: `https://TOKEN@github.com/mmiikkkeee013248/ALVS_project.git`

**Важно**: Не коммитьте токен в код! Используйте его только при клонировании.

## Развертывание

### Вариант 1: Автоматическое развертывание (рекомендуется)

Используйте скрипт `deploy-server.sh`:

```bash
# Клонирование репозитория
git clone https://github.com/mmiikkkeee013248/ALVS_project.git
cd ALVS_project

# Сделать скрипт исполняемым
chmod +x scripts/deploy-server.sh

# Запуск развертывания (можно указать URL репозитория, если нужно)
./scripts/deploy-server.sh
```

Скрипт автоматически:
- Проверит все требования
- Применит production конфигурацию
- Соберет и запустит контейнер

### Вариант 2: Ручное развертывание

#### Шаг 1: Клонирование репозитория

```bash
# Используйте HTTPS для публичного репозитория
git clone https://github.com/mmiikkkeee013248/ALVS_project.git
cd ALVS_project

# Или для приватного репозитория с токеном:
# git clone https://YOUR_TOKEN@github.com/mmiikkkeee013248/ALVS_project.git
```

#### Шаг 2: Настройка переменных окружения

```bash
cd config/docker

# Создание .env файла из примера
cp env.prod.example .env

# Редактирование .env файла
nano .env
```

Установите правильные значения:
```env
PG_HOST=host.docker.internal  # или IP адрес сервера
PG_PORT=5432
PG_DB=test_db
PG_USER=postgres
PG_PASSWORD=your_password
```

**Важно**: Если `host.docker.internal` не работает, используйте IP адрес сервера:
```bash
# Получить IP адрес
hostname -I | awk '{print $1}'
```

#### Шаг 3: Сборка образа

```bash
# Из директории config/docker
docker-compose -f docker-compose.yml -f docker-compose.prod.yml build webapp
```

#### Шаг 4: Запуск приложения

```bash
# Запуск только webapp (без postgres и мониторинга)
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d webapp
```

#### Шаг 5: Проверка статуса

```bash
# Проверка запущенных контейнеров
docker-compose -f docker-compose.yml -f docker-compose.prod.yml ps

# Просмотр логов
docker-compose -f docker-compose.yml -f docker-compose.prod.yml logs -f webapp
```

#### Шаг 6: Проверка доступности

```bash
# Проверка через curl
curl http://localhost:5000

# Или откройте в браузере
# http://your_server_ip:5000
```

## Оптимизации для экономии места

### Что было оптимизировано

1. **Удален контейнер PostgreSQL** (~200-300 MB)
   - Используется внешний PostgreSQL на хосте
   - Экономия: образ + данные БД

2. **Удалены контейнеры мониторинга** (~400-600 MB)
   - Prometheus, Grafana, Loki, Promtail
   - Можно использовать внешние экземпляры при необходимости

3. **Используется multi-stage build** в Dockerfile
   - Минимизирован размер финального образа

### Дополнительные оптимизации

#### Очистка неиспользуемых Docker ресурсов

```bash
# Удаление неиспользуемых образов
docker image prune -a

# Удаление неиспользуемых volumes
docker volume prune

# Полная очистка (осторожно!)
docker system prune -a --volumes
```

#### Использование более легкого базового образа (опционально)

Если нужно еще больше сэкономить место, можно использовать `python:3.12-alpine` вместо `python:3.12-slim`:

```dockerfile
FROM python:3.12-alpine as builder
# ...
```

## Управление развертыванием

### Просмотр логов

```bash
# Все логи (используйте правильную команду для вашей версии)
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs webapp
# или
docker-compose -f docker-compose.yml -f docker-compose.prod.yml logs webapp

# Последние 50 строк
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs --tail=50 webapp

# Логи в реальном времени
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f webapp
```

### Перезапуск приложения

```bash
# Перезапуск
docker compose -f docker-compose.yml -f docker-compose.prod.yml restart webapp

# Пересборка и перезапуск
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build webapp
```

### Остановка приложения

```bash
# Остановка
docker compose -f docker-compose.yml -f docker-compose.prod.yml stop webapp

# Остановка и удаление контейнера
docker compose -f docker-compose.yml -f docker-compose.prod.yml down
```

### Обновление приложения

```bash
# Обновление кода
git pull

# Пересборка и перезапуск
cd config/docker
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build webapp
```

## Устранение неполадок

### Проблема: Контейнер не может подключиться к PostgreSQL

**Решение 1**: Используйте IP адрес сервера вместо `host.docker.internal`:

```bash
# Получить IP
hostname -I | awk '{print $1}'

# Обновить .env
PG_HOST=<IP_адрес_сервера>
```

**Решение 2**: Используйте `network_mode: host` в docker-compose.prod.yml:

```yaml
webapp:
  network_mode: host
  # Уберите networks и ports mapping
```

**Решение 3**: Проверьте настройки PostgreSQL (см. раздел "Настройка доступа PostgreSQL для Docker")

### Проблема: Недостаточно места на диске

```bash
# Проверка использования места
df -h
docker system df

# Очистка
docker system prune -a
```

### Проблема: Порт 5000 уже занят

Измените порт в `docker-compose.prod.yml`:

```yaml
webapp:
  ports:
    - "8080:5000"  # Внешний порт:внутренний порт
```

### Проблема: Приложение возвращает пустую страницу

**Причина**: Неправильная команда запуска Gunicorn или ошибка подключения к PostgreSQL.

**Решение 1**: Проверьте логи контейнера:
```bash
docker logs flask-app
docker compose -f config/docker/docker-compose.prod.yml logs webapp
```

**Решение 2**: Проверьте подключение к PostgreSQL:
```bash
# Из контейнера
docker exec flask-app psql -h host.docker.internal -U postgres -d test_db -c "SELECT version();"

# Если host.docker.internal не работает, используйте IP сервера
docker exec flask-app psql -h 144.31.87.154 -U postgres -d test_db -c "SELECT version();"
```

**Решение 3**: Если `host.docker.internal` не работает, используйте IP адрес сервера:
```bash
# Получить IP адрес
hostname -I | awk '{print $1}'

# Обновить .env файл
nano config/docker/.env
# Измените PG_HOST на IP адрес сервера, например:
# PG_HOST=144.31.87.154

# Перезапустите контейнер
docker compose -f config/docker/docker-compose.prod.yml restart webapp
```

### Проблема: Grafana не видит Prometheus

Если Grafana запущена, но Prometheus не включен в production конфигурацию:

**Решение 1**: Запустите Prometheus отдельно:
```bash
# Запуск Prometheus контейнера
docker run -d \
  --name prometheus \
  -p 9090:9090 \
  -v $(pwd)/config/monitoring/prometheus.yml:/etc/prometheus/prometheus.yml \
  prom/prometheus:latest
```

**Решение 2**: Настройте Grafana для подключения к Prometheus:
1. Зайдите в Grafana: http://144.31.87.154:3000
2. Configuration → Data Sources → Add data source
3. Выберите Prometheus
4. URL: `http://144.31.87.154:9090` (или `http://prometheus:9090` если в одной сети Docker)
5. Save & Test

**Решение 3**: Если Prometheus запущен в другой сети Docker, используйте IP адрес хоста:
- URL в Grafana: `http://144.31.87.154:9090`

### Проблема: Приложение не запускается

```bash
# Проверка логов (используйте правильную команду для вашей версии Docker Compose)
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs webapp
# или
docker-compose -f docker-compose.yml -f docker-compose.prod.yml logs webapp

# Проверка статуса контейнера
docker ps -a

# Вход в контейнер для отладки
docker exec -it flask-app /bin/bash
```

### Проблема: Docker Compose не найден

Если установлен Docker Compose V2 (`docker compose`), но скрипт ищет V1 (`docker-compose`):

Скрипт `deploy-server.sh` автоматически определяет доступную версию. Если проблема сохраняется, проверьте:

```bash
# Проверка Docker Compose V2
docker compose version

# Проверка Docker Compose V1
docker-compose --version
```

## Безопасность

### Рекомендации

1. **Измените пароль PostgreSQL** на надежный
2. **Ограничьте доступ к PostgreSQL** только необходимым IP адресам
3. **Используйте firewall** для ограничения доступа к портам
4. **Регулярно обновляйте** Docker образы и базовую систему
5. **Не храните пароли** в открытом виде в .env файле (используйте секреты)

### Настройка firewall

```bash
# Ubuntu/Debian (ufw)
sudo ufw allow 22/tcp    # SSH
sudo ufw allow 5000/tcp  # Flask приложение
sudo ufw enable

# CentOS/RHEL (firewalld)
sudo firewall-cmd --permanent --add-port=5000/tcp
sudo firewall-cmd --reload
```

## Мониторинг (опционально)

Если нужен мониторинг, можно:

1. **Использовать внешние экземпляры** Prometheus/Grafana
2. **Добавить обратно контейнеры** в docker-compose.prod.yml при необходимости
3. **Использовать системные утилиты** (htop, netstat, journalctl)

## Дополнительная информация

- [Основная документация проекта](../README.md)
- [Docker документация](DOCKER.md)
- [Структура проекта](../STRUCTURE.md)

## Отчет о развертывании

После успешного развертывания заполните отчет в `docs/DEPLOYMENT_REPORT.md` для документирования конфигурации.
