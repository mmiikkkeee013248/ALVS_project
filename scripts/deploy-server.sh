#!/bin/bash

# Скрипт развертывания ALVS_project на сервере с использованием внешних утилит
# Использование: ./scripts/deploy-server.sh [repository_url]

set -e  # Остановка при ошибке

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== Развертывание ALVS_project на сервере ===${NC}"

# Функция проверки команды
check_command() {
    if command -v "$1" &> /dev/null; then
        echo -e "${GREEN}✓${NC} $1 установлен: $(command -v $1)"
        return 0
    else
        echo -e "${RED}✗${NC} $1 не найден"
        return 1
    fi
}

# Функция проверки сервиса
check_service() {
    if systemctl is-active --quiet "$1"; then
        echo -e "${GREEN}✓${NC} Сервис $1 запущен"
        return 0
    else
        echo -e "${YELLOW}⚠${NC} Сервис $1 не запущен"
        return 1
    fi
}

# 1. Проверка системных требований
echo -e "\n${YELLOW}1. Проверка системных требований...${NC}"

# Проверка Docker
if ! check_command docker; then
    echo -e "${RED}Ошибка: Docker не установлен. Установите Docker перед продолжением.${NC}"
    exit 1
fi

# Проверка Docker Compose и определение команды
DOCKER_COMPOSE_CMD=""
if check_command docker-compose; then
    DOCKER_COMPOSE_CMD="docker-compose"
    echo -e "${GREEN}  Используется Docker Compose V1${NC}"
elif docker compose version &> /dev/null; then
    DOCKER_COMPOSE_CMD="docker compose"
    echo -e "${GREEN}  Используется Docker Compose V2${NC}"
else
    echo -e "${RED}Ошибка: Docker Compose не установлен.${NC}"
    echo -e "${YELLOW}Установите Docker Compose V1 (docker-compose) или V2 (docker compose)${NC}"
    exit 1
fi

# Проверка PostgreSQL
if check_command psql; then
    PSQL_VERSION=$(psql --version | head -n1)
    echo -e "${GREEN}  Версия PostgreSQL: ${PSQL_VERSION}${NC}"
    
    # Проверка доступности PostgreSQL
    if check_service postgresql || check_service postgres; then
        echo -e "${GREEN}  PostgreSQL сервис активен${NC}"
    else
        echo -e "${YELLOW}  Предупреждение: PostgreSQL сервис не запущен. Убедитесь, что он доступен.${NC}"
    fi
else
    echo -e "${YELLOW}⚠ PostgreSQL клиент не найден. Убедитесь, что PostgreSQL установлен и доступен.${NC}"
fi

# Проверка свободного места
echo -e "\n${YELLOW}Проверка дискового пространства:${NC}"
df -h / | tail -n1

# 2. Определение директории проекта
REPO_URL="${1:-}"
PROJECT_DIR="ALVS_project"

# Проверка, находимся ли мы уже внутри репозитория
if [ -d ".git" ]; then
    echo -e "\n${YELLOW}2. Обновление существующего репозитория...${NC}"
    git pull || echo -e "${YELLOW}Предупреждение: не удалось обновить репозиторий${NC}"
    # Остаемся в текущей директории
elif [ -d "$PROJECT_DIR" ]; then
    echo -e "\n${YELLOW}2. Обновление существующего репозитория...${NC}"
    cd "$PROJECT_DIR"
    git pull || echo -e "${YELLOW}Предупреждение: не удалось обновить репозиторий${NC}"
else
    if [ -z "$REPO_URL" ]; then
        echo -e "${RED}Ошибка: Репозиторий не найден и URL не указан.${NC}"
        echo -e "Использование: $0 [repository_url]"
        echo -e "Или запустите скрипт из директории проекта"
        exit 1
    fi
    
    echo -e "\n${YELLOW}2. Клонирование репозитория...${NC}"
    git clone "$REPO_URL" "$PROJECT_DIR"
    cd "$PROJECT_DIR"
fi

# 3. Применение локальных изменений для production
echo -e "\n${YELLOW}3. Применение production конфигурации...${NC}"

# Проверка наличия docker-compose.prod.yml
if [ ! -f "config/docker/docker-compose.prod.yml" ]; then
    echo -e "${RED}Ошибка: config/docker/docker-compose.prod.yml не найден${NC}"
    exit 1
fi

# Создание .env файла, если его нет
if [ ! -f "config/docker/.env" ]; then
    echo -e "${YELLOW}Создание .env файла из примера...${NC}"
    if [ -f "config/docker/env.prod.example" ]; then
        cp config/docker/env.prod.example config/docker/.env
        echo -e "${YELLOW}⚠ Не забудьте отредактировать config/docker/.env с правильными настройками PostgreSQL!${NC}"
    else
        echo -e "${YELLOW}Создание базового .env файла...${NC}"
        cat > config/docker/.env << EOF
# Настройки подключения к внешнему PostgreSQL
PG_HOST=host.docker.internal
PG_PORT=5432
PG_DB=test_db
PG_USER=postgres
PG_PASSWORD=postgres
EOF
        echo -e "${YELLOW}⚠ Не забудьте отредактировать config/docker/.env с правильными настройками!${NC}"
    fi
fi

# 4. Настройка PostgreSQL (если нужно)
echo -e "\n${YELLOW}4. Проверка настроек PostgreSQL...${NC}"

# Чтение настроек из .env
if [ -f "config/docker/.env" ]; then
    source config/docker/.env
    echo -e "  Host: ${PG_HOST:-host.docker.internal}"
    echo -e "  Port: ${PG_PORT:-5432}"
    echo -e "  Database: ${PG_DB:-test_db}"
    echo -e "  User: ${PG_USER:-postgres}"
fi

# 5. Сборка и запуск контейнера
echo -e "\n${YELLOW}5. Сборка и запуск Docker контейнера...${NC}"

cd config/docker

# Остановка существующих контейнеров (если есть)
echo -e "Остановка существующих контейнеров..."
$DOCKER_COMPOSE_CMD -f docker-compose.prod.yml down 2>/dev/null || true

# Сборка образа
echo -e "Сборка образа приложения..."
$DOCKER_COMPOSE_CMD -f docker-compose.prod.yml build webapp

# Запуск только webapp (без postgres и мониторинга)
echo -e "Запуск контейнера приложения..."
$DOCKER_COMPOSE_CMD -f docker-compose.prod.yml up -d webapp

# 6. Проверка статуса
echo -e "\n${YELLOW}6. Проверка статуса контейнеров...${NC}"
$DOCKER_COMPOSE_CMD -f docker-compose.prod.yml ps

# 7. Проверка логов
echo -e "\n${YELLOW}7. Последние логи приложения:${NC}"
$DOCKER_COMPOSE_CMD -f docker-compose.prod.yml logs --tail=20 webapp

# 8. Проверка доступности
echo -e "\n${YELLOW}8. Проверка доступности приложения...${NC}"
sleep 3
if curl -f http://localhost:5000 > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Приложение доступно на http://localhost:5000${NC}"
else
    echo -e "${YELLOW}⚠ Приложение может быть еще не готово. Проверьте логи:${NC}"
    echo -e "  $DOCKER_COMPOSE_CMD -f docker-compose.prod.yml logs webapp"
fi

echo -e "\n${GREEN}=== Развертывание завершено ===${NC}"
echo -e "\nПолезные команды:"
echo -e "  Просмотр логов: $DOCKER_COMPOSE_CMD -f docker-compose.prod.yml logs -f webapp"
echo -e "  Остановка: $DOCKER_COMPOSE_CMD -f docker-compose.prod.yml down"
echo -e "  Перезапуск: $DOCKER_COMPOSE_CMD -f docker-compose.prod.yml restart webapp"
