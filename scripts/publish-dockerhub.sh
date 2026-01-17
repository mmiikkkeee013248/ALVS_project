#!/bin/bash

# Скрипт для публикации Docker образа на Docker Hub
# Использование: ./scripts/publish-dockerhub.sh [version] [username]
# Пример: ./scripts/publish-dockerhub.sh 1.0.0 myusername

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Параметры
VERSION=${1:-latest}
DOCKERHUB_USERNAME=${2:-${DOCKERHUB_USERNAME}}

# Имя образа
IMAGE_NAME="alvs-project"

# Проверка наличия Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Ошибка: Docker не установлен${NC}"
    exit 1
fi

# Проверка авторизации в Docker Hub
if ! docker info | grep -q "Username"; then
    if [ -z "$DOCKERHUB_USERNAME" ]; then
        echo -e "${YELLOW}Внимание: Вы не авторизованы в Docker Hub${NC}"
        echo "Выполните: docker login"
        echo "Или установите переменные окружения:"
        echo "  export DOCKERHUB_USERNAME=your_username"
        echo "  export DOCKERHUB_TOKEN=your_token"
        read -p "Продолжить без авторизации? (y/n) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
fi

# Определение пути к корню проекта
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo -e "${GREEN}=== Публикация Docker образа на Docker Hub ===${NC}"
echo "Проект: $PROJECT_ROOT"
echo "Версия: $VERSION"
echo "Имя образа: $IMAGE_NAME"

# Переход в корень проекта
cd "$PROJECT_ROOT"

# Полное имя образа
if [ -n "$DOCKERHUB_USERNAME" ]; then
    FULL_IMAGE_NAME="${DOCKERHUB_USERNAME}/${IMAGE_NAME}"
else
    # Пробуем получить имя пользователя из docker info
    USERNAME=$(docker info 2>/dev/null | grep "Username" | awk '{print $2}' || echo "")
    if [ -z "$USERNAME" ]; then
        echo -e "${RED}Ошибка: Не указан Docker Hub username${NC}"
        echo "Использование: $0 [version] [username]"
        echo "Или установите: export DOCKERHUB_USERNAME=your_username"
        exit 1
    fi
    FULL_IMAGE_NAME="${USERNAME}/${IMAGE_NAME}"
fi

echo -e "${GREEN}Полное имя образа: ${FULL_IMAGE_NAME}:${VERSION}${NC}"

# Сборка образа
echo -e "${YELLOW}Сборка Docker образа...${NC}"
docker build \
    -f config/docker/Dockerfile \
    -t "${FULL_IMAGE_NAME}:${VERSION}" \
    -t "${FULL_IMAGE_NAME}:latest" \
    .

if [ $? -ne 0 ]; then
    echo -e "${RED}Ошибка при сборке образа${NC}"
    exit 1
fi

echo -e "${GREEN}Образ успешно собран${NC}"

# Публикация образа
echo -e "${YELLOW}Публикация образа на Docker Hub...${NC}"
docker push "${FULL_IMAGE_NAME}:${VERSION}"
docker push "${FULL_IMAGE_NAME}:latest"

if [ $? -ne 0 ]; then
    echo -e "${RED}Ошибка при публикации образа${NC}"
    echo "Убедитесь, что вы авторизованы: docker login"
    exit 1
fi

echo -e "${GREEN}=== Образ успешно опубликован ===${NC}"
echo "Имя образа: ${FULL_IMAGE_NAME}"
echo "Теги: ${VERSION}, latest"
echo ""
echo "Использование:"
echo "  docker pull ${FULL_IMAGE_NAME}:${VERSION}"
echo "  docker pull ${FULL_IMAGE_NAME}:latest"
echo ""
echo "Запуск:"
echo "  docker run -d --name flask-app -p 5000:5000 \\"
echo "    -e PG_HOST=postgres_host \\"
echo "    -e PG_DB=test_db \\"
echo "    -e PG_USER=postgres \\"
echo "    -e PG_PASSWORD=password \\"
echo "    ${FULL_IMAGE_NAME}:${VERSION}"
