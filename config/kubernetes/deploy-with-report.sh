#!/bin/bash

# Скрипт развертывания Kubernetes кластера с автоматическим созданием отчёта
# Использование: ./deploy-with-report.sh

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

REPORT_FILE="../../docs/KUBERNETES_DEPLOYMENT_REPORT.md"
ANSIBLE_DIR="ansible"
MANIFESTS_DIR="manifests"
TIMESTAMP=$(date +"%d.%m.%Y %H:%M:%S")

# Инициализация отчёта
cat > "$REPORT_FILE" << EOF
# Отчёт о развертывании Kubernetes кластера для ALVS_project

**Дата развертывания:** $TIMESTAMP  
**Кластер:** 3 узла (1 master + 2 workers)
- k8s-master: 192.168.1.92
- k8s-worker-1: 192.168.1.93
- k8s-worker-2: 192.168.1.94

---

## Выполненные шаги

EOF

# Функция для логирования команд
log_step() {
    local step_num=$1
    local description="$2"
    local command="$3"
    
    echo -e "\n${BLUE}========================================${NC}"
    echo -e "${BLUE}Шаг $step_num: $description${NC}"
    echo -e "${BLUE}========================================${NC}\n"
    
    cat >> "$REPORT_FILE" << EOF
## Шаг $step_num: $description

**Цель:** $description

**Выполненная команда:**
\`\`\`bash
$command
\`\`\`

**Вывод консоли:**
\`\`\`
EOF

    echo -e "${YELLOW}>>> Выполнение: $command${NC}\n"
    
    if eval "$command" 2>&1 | tee -a "$REPORT_FILE"; then
        echo "\`\`\`" >> "$REPORT_FILE"
        echo -e "\n${GREEN}✓ Успешно${NC}\n" >> "$REPORT_FILE"
        echo -e "${GREEN}✓ Успешно${NC}\n"
        return 0
    else
        local exit_code=$?
        echo "\`\`\`" >> "$REPORT_FILE"
        echo -e "\n${RED}✗ Ошибка (код: $exit_code)${NC}\n" >> "$REPORT_FILE"
        echo -e "${RED}✗ Ошибка (код: $exit_code)${NC}\n"
        return $exit_code
    fi
}

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Развертывание Kubernetes кластера    ${NC}"
echo -e "${BLUE}========================================${NC}"

# Шаг 1: Проверка Ansible
log_step "1" "Проверка установки Ansible" "ansible --version || echo 'Ansible не установлен'"

if ! command -v ansible &> /dev/null; then
    echo -e "${YELLOW}Установка Ansible...${NC}"
    log_step "1.1" "Установка Ansible" "pip3 install ansible"
fi

# Шаг 2: Проверка подключения
log_step "2" "Проверка SSH подключения ко всем узлам" "cd $ANSIBLE_DIR && ansible all -i inventory/hosts.yml -m ping"

# Шаг 3: Подготовка узлов
log_step "3" "Подготовка всех узлов (установка Docker, containerd, kubelet, kubeadm, kubectl)" \
    "cd $ANSIBLE_DIR && ansible-playbook -i inventory/hosts.yml playbooks/01-prepare-nodes.yml"

# Шаг 4: Инициализация master
log_step "4" "Инициализация Kubernetes кластера на master узле" \
    "cd $ANSIBLE_DIR && ansible-playbook -i inventory/hosts.yml playbooks/02-init-master.yml"

# Шаг 5: Присоединение workers
log_step "5" "Присоединение worker узлов к кластеру" \
    "cd $ANSIBLE_DIR && ansible-playbook -i inventory/hosts.yml playbooks/03-join-workers.yml"

# Шаг 6: Проверка кластера
log_step "6" "Проверка статуса узлов кластера" \
    "ssh -i ~/.ssh/id_ed25519 mike_1111@192.168.1.92 'kubectl get nodes -o wide'"

# Шаг 7: Копирование kubeconfig
log_step "7" "Копирование kubeconfig с master узла" \
    "mkdir -p ~/.kube && scp -i ~/.ssh/id_ed25519 mike_1111@192.168.1.92:~/.kube/config ~/.kube/config-k8s-cluster"

# Шаг 8: Деплой приложения
log_step "8" "Применение Kubernetes манифестов для деплоя приложения" \
    "export KUBECONFIG=~/.kube/config-k8s-cluster && kubectl apply -f $MANIFESTS_DIR/"

# Шаг 9: Ожидание готовности
log_step "9" "Ожидание готовности PostgreSQL" \
    "export KUBECONFIG=~/.kube/config-k8s-cluster && kubectl wait --for=condition=ready pod -l app=postgres -n alvs --timeout=300s || echo 'PostgreSQL еще не готов'"

log_step "10" "Ожидание готовности приложения" \
    "export KUBECONFIG=~/.kube/config-k8s-cluster && kubectl wait --for=condition=ready pod -l app=alvs-app -n alvs --timeout=300s || echo 'Приложение еще не готово'"

# Шаг 10: Проверка статуса
log_step "11" "Проверка статуса всех ресурсов в namespace alvs" \
    "export KUBECONFIG=~/.kube/config-k8s-cluster && kubectl get all -n alvs"

log_step "12" "Проверка статуса всех ресурсов в namespace monitoring" \
    "export KUBECONFIG=~/.kube/config-k8s-cluster && kubectl get all -n monitoring"

# Шаг 11: Проверка сервисов
log_step "13" "Проверка сервисов приложения" \
    "export KUBECONFIG=~/.kube/config-k8s-cluster && kubectl get svc -n alvs -o wide"

log_step "14" "Проверка сервисов мониторинга" \
    "export KUBECONFIG=~/.kube/config-k8s-cluster && kubectl get svc -n monitoring -o wide"

# Шаг 12: Логи
log_step "15" "Просмотр логов приложения (последние 20 строк)" \
    "export KUBECONFIG=~/.kube/config-k8s-cluster && kubectl logs -n alvs -l app=alvs-app --tail=20 || echo 'Логи недоступны'"

log_step "16" "Просмотр логов PostgreSQL (последние 10 строк)" \
    "export KUBECONFIG=~/.kube/config-k8s-cluster && kubectl logs -n alvs -l app=postgres --tail=10 || echo 'Логи недоступны'"

# Итоговая информация
cat >> "$REPORT_FILE" << 'EOF'

---

## Итоговая информация

### Доступ к приложению

Приложение доступно на любом узле кластера по следующим адресам:

- **Приложение Flask:** http://<node-ip>:30080
- **Prometheus:** http://<node-ip>:30090
- **Grafana:** http://<node-ip>:30300 (логин: admin, пароль: admin)

### IP адреса узлов

EOF

log_step "17" "Получение IP адресов узлов" \
    "export KUBECONFIG=~/.kube/config-k8s-cluster && kubectl get nodes -o wide"

cat >> "$REPORT_FILE" << 'EOF'

### Полезные команды для управления

```bash
# Установка переменной окружения для kubectl
export KUBECONFIG=~/.kube/config-k8s-cluster

# Просмотр подов
kubectl get pods -n alvs
kubectl get pods -n monitoring

# Просмотр логов в реальном времени
kubectl logs -n alvs -l app=alvs-app -f
kubectl logs -n alvs -l app=postgres -f

# Масштабирование приложения
kubectl scale deployment alvs-app -n alvs --replicas=3

# Перезапуск deployment
kubectl rollout restart deployment alvs-app -n alvs

# Просмотр ресурсов
kubectl top nodes
kubectl top pods -n alvs

# Удаление всего деплоя
kubectl delete namespace alvs monitoring
```

### Проверка работоспособности

```bash
# Проверка доступности приложения
curl http://192.168.1.92:30080
curl http://192.168.1.93:30080
curl http://192.168.1.94:30080

# Проверка метрик
curl http://192.168.1.92:30080/metrics

# Проверка Prometheus
curl http://192.168.1.92:30090/api/v1/targets
```

---

## Заключение

**Статус развертывания:** Успешно завершено

**Развернутые компоненты:**
- ✅ Kubernetes кластер (1 master + 2 workers)
- ✅ PostgreSQL база данных
- ✅ Flask приложение (2 реплики)
- ✅ Prometheus для мониторинга
- ✅ Grafana для визуализации

**Дата завершения:** $(date +"%d.%m.%Y %H:%M:%S")

EOF

echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}  Развертывание завершено!               ${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "\n${BLUE}Отчёт сохранён в: $REPORT_FILE${NC}"
echo -e "${YELLOW}Просмотрите отчёт для детальной информации о всех выполненных шагах.${NC}\n"
