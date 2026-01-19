#!/bin/bash

# Скрипт для деплоя ALVS_project в Kubernetes кластер
# Использование: ./deploy.sh [kubeconfig_path]

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

KUBECONFIG_PATH="${1:-$HOME/.kube/config}"
MANIFESTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/manifests"

echo -e "${GREEN}=== Деплой ALVS_project в Kubernetes ===${NC}"

# Проверка kubectl
if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}Ошибка: kubectl не установлен${NC}"
    exit 1
fi

# Проверка подключения к кластеру
if ! kubectl --kubeconfig="$KUBECONFIG_PATH" cluster-info &> /dev/null; then
    echo -e "${RED}Ошибка: Не удалось подключиться к кластеру${NC}"
    echo -e "${YELLOW}Проверьте KUBECONFIG: $KUBECONFIG_PATH${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Подключение к кластеру успешно${NC}"

# Применение манифестов в правильном порядке
echo -e "\n${YELLOW}1. Создание namespace...${NC}"
kubectl --kubeconfig="$KUBECONFIG_PATH" apply -f "$MANIFESTS_DIR/00-namespace.yml"

echo -e "\n${YELLOW}2. Создание StorageClass...${NC}"
kubectl --kubeconfig="$KUBECONFIG_PATH" apply -f "$MANIFESTS_DIR/10-local-storage-class.yml" || echo -e "${YELLOW}StorageClass уже существует или используется другой${NC}"

echo -e "\n${YELLOW}3. Создание секретов PostgreSQL...${NC}"
kubectl --kubeconfig="$KUBECONFIG_PATH" apply -f "$MANIFESTS_DIR/01-postgres-secret.yml"

echo -e "\n${YELLOW}4. Создание PVC для PostgreSQL...${NC}"
kubectl --kubeconfig="$KUBECONFIG_PATH" apply -f "$MANIFESTS_DIR/02-postgres-pvc.yml"

echo -e "\n${YELLOW}5. Деплой PostgreSQL...${NC}"
kubectl --kubeconfig="$KUBECONFIG_PATH" apply -f "$MANIFESTS_DIR/03-postgres-deployment.yml"

echo -e "\n${YELLOW}6. Ожидание готовности PostgreSQL...${NC}"
kubectl --kubeconfig="$KUBECONFIG_PATH" wait --for=condition=ready pod -l app=postgres -n alvs --timeout=300s || echo -e "${YELLOW}Предупреждение: PostgreSQL еще не готов${NC}"

echo -e "\n${YELLOW}7. Создание ConfigMap и Secret для приложения...${NC}"
kubectl --kubeconfig="$KUBECONFIG_PATH" apply -f "$MANIFESTS_DIR/04-app-configmap.yml"
kubectl --kubeconfig="$KUBECONFIG_PATH" apply -f "$MANIFESTS_DIR/05-app-secret.yml"

echo -e "\n${YELLOW}8. Деплой приложения...${NC}"
kubectl --kubeconfig="$KUBECONFIG_PATH" apply -f "$MANIFESTS_DIR/06-app-deployment.yml"

echo -e "\n${YELLOW}9. Деплой мониторинга...${NC}"
kubectl --kubeconfig="$KUBECONFIG_PATH" apply -f "$MANIFESTS_DIR/07-prometheus-configmap.yml"
kubectl --kubeconfig="$KUBECONFIG_PATH" apply -f "$MANIFESTS_DIR/08-prometheus-deployment.yml"
kubectl --kubeconfig="$KUBECONFIG_PATH" apply -f "$MANIFESTS_DIR/09-grafana-deployment.yml"

echo -e "\n${YELLOW}10. Ожидание готовности подов...${NC}"
sleep 10

echo -e "\n${GREEN}=== Статус деплоя ===${NC}"
kubectl --kubeconfig="$KUBECONFIG_PATH" get pods -n alvs
kubectl --kubeconfig="$KUBECONFIG_PATH" get pods -n monitoring

echo -e "\n${GREEN}=== Сервисы ===${NC}"
kubectl --kubeconfig="$KUBECONFIG_PATH" get svc -n alvs
kubectl --kubeconfig="$KUBECONFIG_PATH" get svc -n monitoring

echo -e "\n${GREEN}=== Доступ к приложению ===${NC}"
echo -e "Приложение доступно на любом узле по адресу:"
echo -e "  http://<node-ip>:30080"
echo -e ""
echo -e "Prometheus доступен на:"
echo -e "  http://<node-ip>:30090"
echo -e ""
echo -e "Grafana доступна на:"
echo -e "  http://<node-ip>:30300 (admin/admin)"
echo -e ""
echo -e "Для получения IP адресов узлов:"
echo -e "  kubectl --kubeconfig=$KUBECONFIG_PATH get nodes -o wide"
