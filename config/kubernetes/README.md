# Kubernetes Deployment для ALVS_project

Быстрый старт для развертывания Kubernetes кластера и приложения ALVS_project.

## Структура

```
config/kubernetes/
├── ansible/                    # Ansible конфигурация для развертывания кластера
│   ├── inventory/             # Inventory файлы
│   ├── group_vars/            # Переменные для групп
│   ├── playbooks/            # Ansible playbooks
│   └── ansible.cfg           # Конфигурация Ansible
├── manifests/                 # Kubernetes манифесты
│   ├── 00-namespace.yml      # Namespaces
│   ├── 01-postgres-secret.yml
│   ├── 02-postgres-pvc.yml
│   ├── 03-postgres-deployment.yml
│   ├── 04-app-configmap.yml
│   ├── 05-app-secret.yml
│   ├── 06-app-deployment.yml
│   ├── 07-prometheus-configmap.yml
│   ├── 08-prometheus-deployment.yml
│   ├── 09-grafana-deployment.yml
│   └── 10-local-storage-class.yml
├── deploy.sh                  # Скрипт для деплоя
└── README.md                 # Этот файл
```

## Быстрый старт

### 1. Подготовка

```bash
# Установка Ansible (на управляющей машине)
pip3 install ansible

# Клонирование репозитория
git clone https://github.com/mmiikkkeee013248/ALVS_project.git
cd ALVS_project
```

### 2. Настройка inventory

Отредактируйте `config/kubernetes/ansible/inventory/hosts.yml` с вашими IP адресами и пользователями.

### 3. Развертывание кластера

```bash
cd config/kubernetes/ansible

# Проверка подключения
ansible all -i inventory/hosts.yml -m ping

# Подготовка узлов
ansible-playbook -i inventory/hosts.yml playbooks/01-prepare-nodes.yml

# Инициализация master
ansible-playbook -i inventory/hosts.yml playbooks/02-init-master.yml

# Присоединение workers
ansible-playbook -i inventory/hosts.yml playbooks/03-join-workers.yml
```

### 4. Деплой приложения

```bash
cd config/kubernetes

# Использование скрипта
./deploy.sh

# Или вручную
kubectl apply -f manifests/
```

## Подробная документация

См. [docs/KUBERNETES_DEPLOYMENT.md](../../docs/KUBERNETES_DEPLOYMENT.md)
