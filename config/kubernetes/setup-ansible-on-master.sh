#!/bin/bash

# Скрипт для установки Ansible на k8s-master и настройки развертывания
# Использование: ./setup-ansible-on-master.sh

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

REPORT_FILE="../../docs/KUBERNETES_DEPLOYMENT_REPORT.md"
ANSIBLE_DIR="ansible"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Установка Ansible на k8s-master      ${NC}"
echo -e "${BLUE}========================================${NC}"

# Инициализация отчёта
TIMESTAMP=$(date +"%d.%m.%Y %H:%M:%S")
cat > "$REPORT_FILE" << EOF
# Отчёт о развертывании Kubernetes кластера для ALVS_project

**Дата развертывания:** $TIMESTAMP  
**Кластер:** 3 узла (1 master + 2 workers)
- k8s-master: 192.168.1.92 (Ansible control node)
- k8s-worker-1: 192.168.1.93
- k8s-worker-2: 192.168.1.94

**Особенность:** Ansible установлен на k8s-master, развертывание выполняется с master узла.

---

## Выполненные шаги

EOF

# Функция для логирования
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

# Шаг 1: Проверка подключения к master
log_step "1" "Проверка SSH подключения к k8s-master" \
    "ssh -i ~/.ssh/id_ed25519 -o StrictHostKeyChecking=no mike_1111@192.168.1.92 'echo \"Подключение успешно\"'"

# Шаг 2: Установка Ansible на master
log_step "2" "Установка Ansible на k8s-master" \
    "cd $ANSIBLE_DIR && ansible-playbook -i inventory/hosts.yml playbooks/00-install-ansible-on-master.yml"

# Шаг 3: Проверка установки Ansible
log_step "3" "Проверка установки Ansible на master" \
    "ssh -i ~/.ssh/id_ed25519 mike_1111@192.168.1.92 'ansible --version'"

# Шаг 4: Развертывание кластера с master
log_step "4" "Развертывание Kubernetes кластера с k8s-master" \
    "cd $ANSIBLE_DIR && ansible-playbook -i inventory/hosts.yml playbooks/04-deploy-from-master.yml"

# Шаг 5: Копирование kubeconfig для локального доступа
log_step "5" "Копирование kubeconfig с master для локального доступа" \
    "mkdir -p ~/.kube && scp -i ~/.ssh/id_ed25519 mike_1111@192.168.1.92:~/.kube/config ~/.kube/config-k8s-cluster"

# Шаг 6: Проверка кластера локально
log_step "6" "Проверка статуса кластера" \
    "export KUBECONFIG=~/.kube/config-k8s-cluster && kubectl get nodes -o wide"

# Шаг 7: Проверка деплоя приложения
log_step "7" "Проверка статуса приложения" \
    "export KUBECONFIG=~/.kube/config-k8s-cluster && kubectl get all -n alvs"

log_step "8" "Проверка статуса мониторинга" \
    "export KUBECONFIG=~/.kube/config-k8s-cluster && kubectl get all -n monitoring"

# Итоговая информация
cat >> "$REPORT_FILE" << 'EOF'

---

## Итоговая информация

### Архитектура развертывания

```
┌─────────────────────────────────────────────────────────┐
│              k8s-master (192.168.1.92)                  │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Ansible Control Node                            │  │
│  │  - Ansible установлен                            │  │
│  │  - Playbooks для развертывания                  │  │
│  │  - Kubernetes манифесты                         │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Kubernetes Control Plane                        │  │
│  │  - API Server                                    │  │
│  │  - etcd                                          │  │
│  │  - Controller Manager                            │  │
│  │  - Scheduler                                     │  │
│  └──────────────────────────────────────────────────┘  │
│           │                                             │
│           ├──────────────────┬──────────────────┐       │
│           │                  │                  │       │
│  ┌────────▼──────┐  ┌────────▼──────┐          │       │
│  │ k8s-worker-1  │  │ k8s-worker-2  │          │       │
│  │ (192.168.1.93)│  │ (192.168.1.94)│          │       │
│  │               │  │               │          │       │
│  │ - kubelet     │  │ - kubelet     │          │       │
│  │ - kube-proxy  │  │ - kube-proxy  │          │       │
│  │ - Pods        │  │ - Pods        │          │       │
│  └───────────────┘  └───────────────┘          │       │
└─────────────────────────────────────────────────────────┘
```

### Доступ к приложению

После успешного развертывания приложение доступно на любом узле кластера:

- **Приложение Flask:** http://<node-ip>:30080
- **Prometheus:** http://<node-ip>:30090
- **Grafana:** http://<node-ip>:30300 (логин: admin, пароль: admin)

### Управление кластером с master узла

Для управления кластером подключитесь к master узлу:

```bash
ssh -i ~/.ssh/id_ed25519 mike_1111@192.168.1.92
cd ~/ansible-k8s

# Проверка кластера
export KUBECONFIG=~/.kube/config
kubectl get nodes

# Просмотр подов
kubectl get pods -n alvs
kubectl get pods -n monitoring

# Просмотр логов
kubectl logs -n alvs -l app=alvs-app -f
```

### Полезные команды

```bash
# Локальное управление (если скопирован kubeconfig)
export KUBECONFIG=~/.kube/config-k8s-cluster

# Просмотр подов
kubectl get pods -n alvs
kubectl get pods -n monitoring

# Просмотр логов
kubectl logs -n alvs -l app=alvs-app -f
kubectl logs -n alvs -l app=postgres -f

# Масштабирование
kubectl scale deployment alvs-app -n alvs --replicas=3

# Перезапуск deployment
kubectl rollout restart deployment alvs-app -n alvs
```

---

**Развертывание завершено!**

EOF

echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}  Установка и настройка завершены!       ${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "\n${BLUE}Отчёт сохранён в: $REPORT_FILE${NC}"
echo -e "${YELLOW}Ansible установлен на k8s-master в директории: ~/ansible-k8s${NC}\n"
echo -e "${YELLOW}Для управления кластером подключитесь к master:${NC}"
echo -e "  ssh -i ~/.ssh/id_ed25519 mike_1111@192.168.1.92\n"
