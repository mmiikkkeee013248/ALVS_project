# Развертывание Kubernetes с k8s-master как Ansible control node

Этот документ описывает развертывание Kubernetes кластера, где Ansible установлен на k8s-master, и развертывание выполняется с master узла.

## Архитектура

```
┌─────────────────────────────────────────────────────────┐
│  Управляющая машина (ваш компьютер)                     │
│  - SSH подключение к k8s-master                         │
│  - Запуск playbook для установки Ansible на master      │
└──────────────────┬──────────────────────────────────────┘
                   │ SSH
                   ▼
┌─────────────────────────────────────────────────────────┐
│  k8s-master (192.168.1.92)                              │
│  ┌──────────────────────────────────────────────────┐   │
│  │  Ansible Control Node                            │   │
│  │  - Ansible установлен                            │   │
│  │  - Playbooks: ~/ansible-k8s/playbooks/          │   │
│  │  - Inventory: ~/ansible-k8s/inventory/           │   │
│  │  - Manifests: ~/ansible-k8s/manifests/           │   │
│  └──────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────┐   │
│  │  Kubernetes Control Plane                       │   │
│  └──────────────────────────────────────────────────┘   │
│           │ Ansible                                      │
│           ├──────────────────┬──────────────────┐       │
│           │                  │                  │       │
│  ┌────────▼──────┐  ┌────────▼──────┐          │       │
│  │ k8s-worker-1  │  │ k8s-worker-2  │          │       │
│  │ (192.168.1.93)│  │ (192.168.1.94)│          │       │
│  └───────────────┘  └───────────────┘          │       │
└─────────────────────────────────────────────────────────┘
```

## Преимущества такого подхода

1. **Централизованное управление** - все команды выполняются с одного узла
2. **Близость к кластеру** - master узел находится в той же сети
3. **Безопасность** - не нужно устанавливать Ansible на управляющей машине
4. **Удобство** - можно управлять кластером напрямую с master узла

## Процесс развертывания

### Шаг 1: Установка Ansible на k8s-master

С вашей управляющей машины:

```bash
cd config/kubernetes
./setup-ansible-on-master.sh
```

Или вручную:

```bash
cd config/kubernetes/ansible
ansible-playbook -i inventory/hosts.yml playbooks/00-install-ansible-on-master.yml
```

**Что делает:**
- Устанавливает Ansible на k8s-master
- Копирует все playbooks, inventory и манифесты на master
- Настраивает SSH ключи для доступа к workers
- Проверяет подключение

### Шаг 2: Развертывание кластера с master

После установки Ansible на master, развертывание выполняется автоматически через playbook `04-deploy-from-master.yml`, или вручную:

```bash
# Подключение к master
ssh -i ~/.ssh/id_ed25519 mike_1111@192.168.1.92

# Переход в директорию Ansible
cd ~/ansible-k8s

# Развертывание кластера
ansible-playbook -i inventory/hosts.yml playbooks/01-prepare-nodes.yml
ansible-playbook -i inventory/hosts.yml playbooks/02-init-master.yml
ansible-playbook -i inventory/hosts.yml playbooks/03-join-workers.yml

# Деплой приложения
export KUBECONFIG=~/.kube/config
kubectl apply -f manifests/
```

### Шаг 3: Управление кластером

Все команды kubectl выполняются на master узле:

```bash
ssh -i ~/.ssh/id_ed25519 mike_1111@192.168.1.92
export KUBECONFIG=~/.kube/config

# Проверка узлов
kubectl get nodes

# Просмотр подов
kubectl get pods -n alvs
kubectl get pods -n monitoring

# Просмотр логов
kubectl logs -n alvs -l app=alvs-app -f
```

## Структура на master узле

После установки на k8s-master будет создана следующая структура:

```
/home/mike_1111/ansible-k8s/
├── ansible.cfg              # Конфигурация Ansible
├── inventory/
│   └── hosts.yml            # Inventory с узлами кластера
├── group_vars/
│   └── all.yml              # Переменные для всех узлов
├── playbooks/
│   ├── 01-prepare-nodes.yml # Подготовка узлов
│   ├── 02-init-master.yml   # Инициализация master
│   └── 03-join-workers.yml  # Присоединение workers
├── manifests/               # Kubernetes манифесты
│   ├── 00-namespace.yml
│   ├── 01-postgres-secret.yml
│   └── ...
└── deploy.sh                # Скрипт деплоя приложения
```

## Автоматическое развертывание

Для полного автоматического развертывания используйте скрипт:

```bash
cd config/kubernetes
./setup-ansible-on-master.sh
```

Скрипт:
1. Установит Ansible на master
2. Скопирует все необходимые файлы
3. Развернет кластер
4. Задеплоит приложение
5. Создаст отчёт со всеми командами и выводом

## Ручное развертывание

Если нужно выполнить шаги вручную:

### 1. Установка Ansible на master

```bash
cd config/kubernetes/ansible
ansible-playbook -i inventory/hosts.yml playbooks/00-install-ansible-on-master.yml
```

### 2. Развертывание с master

```bash
# Подключение к master
ssh -i ~/.ssh/id_ed25519 mike_1111@192.168.1.92

# Выполнение playbook развертывания
cd ~/ansible-k8s
ansible-playbook -i inventory/hosts.yml playbooks/04-deploy-from-master.yml
```

## Проверка работоспособности

После развертывания проверьте:

```bash
# С master узла
ssh -i ~/.ssh/id_ed25519 mike_1111@192.168.1.92
export KUBECONFIG=~/.kube/config

# Статус узлов
kubectl get nodes -o wide

# Статус подов
kubectl get pods -n alvs -o wide
kubectl get pods -n monitoring -o wide

# Статус сервисов
kubectl get svc -n alvs
kubectl get svc -n monitoring

# Доступность приложения
curl http://192.168.1.92:30080
curl http://192.168.1.93:30080
curl http://192.168.1.94:30080
```

## Устранение проблем

### Проблема: Не удается подключиться к master

**Решение:**
```bash
# Проверка SSH ключа
ssh -i ~/.ssh/id_ed25519 -v mike_1111@192.168.1.92

# Проверка доступности
ping 192.168.1.92
```

### Проблема: Ansible не установился на master

**Решение:**
```bash
# Ручная установка на master
ssh -i ~/.ssh/id_ed25519 mike_1111@192.168.1.92
sudo apt update
sudo apt install -y python3-pip
pip3 install ansible jinja2 pyyaml
```

### Проблема: Workers не присоединяются

**Решение:**
```bash
# С master узла
ssh -i ~/.ssh/id_ed25519 mike_1111@192.168.1.92
cd ~/ansible-k8s

# Проверка подключения к workers
ansible all -i inventory/hosts.yml -m ping

# Повторное присоединение
ansible-playbook -i inventory/hosts.yml playbooks/03-join-workers.yml
```

## Дополнительные команды

### Обновление конфигурации

Если нужно обновить playbooks или манифесты:

```bash
# С управляющей машины
cd config/kubernetes/ansible
ansible-playbook -i inventory/hosts.yml playbooks/00-install-ansible-on-master.yml
```

Это перезапишет файлы на master узле.

### Просмотр логов Ansible

```bash
# На master узле
cd ~/ansible-k8s
ansible-playbook -i inventory/hosts.yml playbooks/01-prepare-nodes.yml -v
```

Флаги для детального вывода:
- `-v` - базовый вывод
- `-vv` - более детальный
- `-vvv` - очень детальный
- `-vvvv` - максимально детальный

---

**Примечание:** Все команды kubectl выполняются на master узле после развертывания. Для локального управления скопируйте kubeconfig:

```bash
scp -i ~/.ssh/id_ed25519 mike_1111@192.168.1.92:~/.kube/config ~/.kube/config-k8s-cluster
export KUBECONFIG=~/.kube/config-k8s-cluster
kubectl get nodes
```
