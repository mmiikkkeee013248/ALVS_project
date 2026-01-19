#!/bin/bash

# Полный скрипт развертывания Kubernetes кластера и приложения
# Использование: ./deploy-cluster.sh

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

REPORT_FILE="../../docs/KUBERNETES_DEPLOYMENT_REPORT.md"
ANSIBLE_DIR="ansible"
MANIFESTS_DIR="manifests"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Развертывание Kubernetes кластера    ${NC}"
echo -e "${BLUE}========================================${NC}"

# Функция для логирования команд и вывода
log_command() {
    local cmd="$1"
    local description="$2"
    
    echo -e "\n${YELLOW}>>> $description${NC}"
    echo -e "${BLUE}Команда: $cmd${NC}"
    echo -e "\n\`\`\`bash" >> "$REPORT_FILE"
    echo "$cmd" >> "$REPORT_FILE"
    echo "\`\`\`" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    
    eval "$cmd" 2>&1 | tee -a "$REPORT_FILE"
    local exit_code=${PIPESTATUS[0]}
    
    echo "\`\`\`" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    
    return $exit_code
}

# Инициализация отчёта
cat > "$REPORT_FILE" << 'EOF'
# Отчёт о развертывании Kubernetes кластера для ALVS_project

**Дата развертывания:** $(date +"%d.%m.%Y %H:%M:%S")  
**Кластер:** 3 узла (1 master + 2 workers)

---

## Выполненные шаги

EOF

echo -e "\n${GREEN}=== Шаг 1: Проверка Ansible ===${NC}"
if ! command -v ansible &> /dev/null; then
    echo -e "${RED}Ansible не установлен. Устанавливаю...${NC}"
    pip3 install ansible
fi

ANSIBLE_VERSION=$(ansible --version | head -n1)
echo -e "${GREEN}✓ Ansible: $ANSIBLE_VERSION${NC}"

log_command "ansible --version" "Проверка версии Ansible"

echo -e "\n${GREEN}=== Шаг 2: Проверка подключения к узлам ===${NC}"
log_command "cd $ANSIBLE_DIR && ansible all -i inventory/hosts.yml -m ping" "Проверка SSH подключения ко всем узлам"

echo -e "\n${GREEN}=== Шаг 3: Подготовка узлов ===${NC}"
log_command "cd $ANSIBLE_DIR && ansible-playbook -i inventory/hosts.yml playbooks/01-prepare-nodes.yml" "Подготовка всех узлов (установка Docker, containerd, kubelet, kubeadm, kubectl)"

echo -e "\n${GREEN}=== Шаг 4: Инициализация master узла ===${NC}"
log_command "cd $ANSIBLE_DIR && ansible-playbook -i inventory/hosts.yml playbooks/02-init-master.yml" "Инициализация Kubernetes кластера на master узле"

echo -e "\n${GREEN}=== Шаг 5: Присоединение worker узлов ===${NC}"
log_command "cd $ANSIBLE_DIR && ansible-playbook -i inventory/hosts.yml playbooks/03-join-workers.yml" "Присоединение worker узлов к кластеру"

echo -e "\n${GREEN}=== Шаг 6: Проверка кластера ===${NC}"
log_command "ssh -i ~/.ssh/id_ed25519 mike_1111@192.168.1.92 'kubectl get nodes -o wide'" "Проверка статуса узлов кластера"

echo -e "\n${GREEN}=== Шаг 7: Копирование kubeconfig ===${NC}"
log_command "mkdir -p ~/.kube && scp -i ~/.ssh/id_ed25519 mike_1111@192.168.1.92:~/.kube/config ~/.kube/config-k8s-cluster" "Копирование kubeconfig с master узла"

echo -e "\n${GREEN}=== Шаг 8: Деплой приложения ===${NC}"
log_command "export KUBECONFIG=~/.kube/config-k8s-cluster && kubectl apply -f $MANIFESTS_DIR/" "Применение всех Kubernetes манифестов"

echo -e "\n${GREEN}=== Шаг 9: Ожидание готовности подов ===${NC}"
log_command "export KUBECONFIG=~/.kube/config-k8s-cluster && kubectl wait --for=condition=ready pod -l app=postgres -n alvs --timeout=300s" "Ожидание готовности PostgreSQL"
log_command "export KUBECONFIG=~/.kube/config-k8s-cluster && kubectl wait --for=condition=ready pod -l app=alvs-app -n alvs --timeout=300s" "Ожидание готовности приложения"

echo -e "\n${GREEN}=== Шаг 10: Проверка статуса деплоя ===${NC}"
log_command "export KUBECONFIG=~/.kube/config-k8s-cluster && kubectl get all -n alvs" "Статус ресурсов в namespace alvs"
log_command "export KUBECONFIG=~/.kube/config-k8s-cluster && kubectl get all -n monitoring" "Статус ресурсов в namespace monitoring"

echo -e "\n${GREEN}=== Шаг 11: Проверка сервисов ===${NC}"
log_command "export KUBECONFIG=~/.kube/config-k8s-cluster && kubectl get svc -n alvs" "Сервисы приложения"
log_command "export KUBECONFIG=~/.kube/config-k8s-cluster && kubectl get svc -n monitoring" "Сервисы мониторинга"

echo -e "\n${GREEN}=== Шаг 12: Проверка логов ===${NC}"
log_command "export KUBECONFIG=~/.kube/config-k8s-cluster && kubectl logs -n alvs -l app=alvs-app --tail=20" "Логи приложения"
log_command "export KUBECONFIG=~/.kube/config-k8s-cluster && kubectl logs -n alvs -l app=postgres --tail=10" "Логи PostgreSQL"

# Добавление итоговой информации в отчёт
cat >> "$REPORT_FILE" << 'EOF'

---

## Итоговая информация

### Доступ к приложению

Приложение доступно на любом узле кластера:
- **Приложение:** http://<node-ip>:30080
- **Prometheus:** http://<node-ip>:30090
- **Grafana:** http://<node-ip>:30300 (admin/admin)

### IP адреса узлов

EOF

log_command "export KUBECONFIG=~/.kube/config-k8s-cluster && kubectl get nodes -o wide" "IP адреса узлов кластера"

cat >> "$REPORT_FILE" << 'EOF'

### Полезные команды

```bash
# Просмотр подов
export KUBECONFIG=~/.kube/config-k8s-cluster
kubectl get pods -n alvs
kubectl get pods -n monitoring

# Просмотр логов
kubectl logs -n alvs -l app=alvs-app -f
kubectl logs -n alvs -l app=postgres -f

# Масштабирование
kubectl scale deployment alvs-app -n alvs --replicas=3

# Удаление деплоя
kubectl delete namespace alvs monitoring
```

---

**Развертывание завершено!**

EOF

echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}  Развертывание завершено успешно!      ${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "\n${BLUE}Отчёт сохранён в: $REPORT_FILE${NC}"
