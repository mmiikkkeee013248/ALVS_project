# Развертывание ALVS_project в Kubernetes кластере

Это руководство описывает процесс развертывания Kubernetes кластера из трех узлов и деплоя приложения ALVS_project.

## Содержание

- [Требования](#требования)
- [Архитектура](#архитектура)
- [Шаг 1: Подготовка Ansible](#шаг-1-подготовка-ansible)
- [Шаг 2: Развертывание Kubernetes кластера](#шаг-2-развертывание-kubernetes-кластера)
- [Шаг 3: Деплой приложения](#шаг-3-деплой-приложения)
- [Шаг 4: Проверка работоспособности](#шаг-4-проверка-работоспособности)
- [Устранение проблем](#устранение-проблем)

---

## Требования

### Управляющая машина (где запускается Ansible)

- Ubuntu/Debian Linux
- Python 3.6+
- Ansible 2.9+
- SSH доступ ко всем узлам кластера
- SSH ключ для доступа к узлам

### Узлы кластера

- Ubuntu 20.04+ или Debian 11+
- Минимум 2 CPU
- Минимум 2 GB RAM
- Минимум 20 GB свободного места
- SSH доступ с управляющей машины
- Отключен swap

### Сетевая конфигурация

- Все узлы должны быть доступны друг другу по сети
- Порты, которые должны быть открыты:
  - 6443 (Kubernetes API)
  - 10250 (Kubelet API)
  - 30080 (Приложение)
  - 30090 (Prometheus)
  - 30300 (Grafana)

---

## Архитектура

```
┌─────────────────────────────────────────────────────────┐
│                    Kubernetes Cluster                    │
│                                                          │
│  ┌──────────────────┐                                   │
│  │  k8s-master      │  (192.168.1.92)                  │
│  │  - Control Plane │                                   │
│  │  - etcd          │                                   │
│  └──────────────────┘                                   │
│           │                                             │
│           ├──────────────────┬──────────────────┐        │
│           │                  │                  │        │
│  ┌────────▼──────┐  ┌────────▼──────┐  ┌──────▼──────┐ │
│  │ k8s-worker-1  │  │ k8s-worker-2  │  │              │ │
│  │ (192.168.1.93)│  │ (192.168.1.94)│  │              │ │
│  │               │  │               │  │              │ │
│  │ - alvs-app    │  │ - alvs-app    │  │ - PostgreSQL │ │
│  │ - Prometheus  │  │ - Grafana     │  │              │ │
│  └───────────────┘  └───────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────┘
```

---

## Шаг 1: Подготовка Ansible

### 1.1. Установка Ansible на управляющей машине

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install -y python3-pip
pip3 install ansible

# Проверка установки
ansible --version
```

### 1.2. Настройка SSH доступа

Убедитесь, что у вас есть SSH ключ и он добавлен в `~/.ssh/id_ed25519`:

```bash
# Проверка SSH подключения
ssh -i ~/.ssh/id_ed25519 mike_1111@192.168.1.92
ssh -i ~/.ssh/id_ed25519 mike_1111@192.168.1.93
ssh -i ~/.ssh/id_ed25519 mike_1111@192.168.1.94
```

### 1.3. Клонирование репозитория

```bash
git clone https://github.com/mmiikkkeee013248/ALVS_project.git
cd ALVS_project
```

---

## Шаг 2: Развертывание Kubernetes кластера

### 2.1. Переход в директорию Ansible

```bash
cd config/kubernetes/ansible
```

### 2.2. Проверка inventory

Убедитесь, что файл `inventory/hosts.yml` содержит правильные IP адреса и пользователей.

### 2.3. Проверка подключения к узлам

```bash
ansible all -i inventory/hosts.yml -m ping
```

Должен быть вывод `SUCCESS` для всех узлов.

### 2.4. Запуск playbook для подготовки узлов

```bash
ansible-playbook -i inventory/hosts.yml playbooks/01-prepare-nodes.yml
```

Этот playbook:
- Обновит систему
- Установит необходимые пакеты
- Настроит containerd
- Установит kubelet, kubeadm, kubectl
- Настроит системные параметры

### 2.5. Инициализация master узла

```bash
ansible-playbook -i inventory/hosts.yml playbooks/02-init-master.yml
```

Этот playbook:
- Инициализирует Kubernetes кластер на master узле
- Установит Calico CNI
- Создаст kubeconfig файл
- Выведет команду для присоединения worker узлов

**Важно:** Сохраните команду join, которая будет выведена в конце.

### 2.6. Присоединение worker узлов

```bash
ansible-playbook -i inventory/hosts.yml playbooks/03-join-workers.yml
```

Этот playbook автоматически присоединит worker узлы к кластеру.

### 2.7. Проверка кластера

```bash
# Подключение к master узлу
ssh -i ~/.ssh/id_ed25519 mike_1111@192.168.1.92

# Проверка узлов
kubectl get nodes

# Должен быть вывод:
# NAME           STATUS   ROLES           AGE   VERSION
# k8s-master     Ready    control-plane   5m    v1.28.0
# k8s-worker-1   Ready    <none>          2m    v1.28.0
# k8s-worker-2   Ready    <none>          2m    v1.28.0
```

### 2.8. Копирование kubeconfig на управляющую машину

```bash
# С master узла
scp -i ~/.ssh/id_ed25519 mike_1111@192.168.1.92:~/.kube/config ~/.kube/config-k8s-cluster

# Или экспорт переменной
export KUBECONFIG=~/.kube/config-k8s-cluster
```

---

## Шаг 3: Деплой приложения

### 3.1. Подготовка Docker образа

Перед деплоем убедитесь, что Docker образ приложения доступен:

**Вариант A: Использование локального образа**

Соберите образ на всех узлах или используйте Docker Hub:

```bash
# На каждом узле
docker build -t alvs-project:latest /path/to/ALVS_project
```

**Вариант B: Использование Docker Hub**

1. Опубликуйте образ на Docker Hub (см. [DOCKER_HUB.md](DOCKER_HUB.md))
2. Обновите `config/kubernetes/manifests/06-app-deployment.yml`:
   ```yaml
   image: your_username/alvs-project:latest
   imagePullPolicy: Always
   ```

### 3.2. Деплой приложения

```bash
cd config/kubernetes

# Использование скрипта
chmod +x deploy.sh
./deploy.sh ~/.kube/config-k8s-cluster

# Или вручную
kubectl --kubeconfig=~/.kube/config-k8s-cluster apply -f manifests/
```

### 3.3. Проверка статуса деплоя

```bash
# Проверка подов
kubectl --kubeconfig=~/.kube/config-k8s-cluster get pods -n alvs
kubectl --kubeconfig=~/.kube/config-k8s-cluster get pods -n monitoring

# Проверка сервисов
kubectl --kubeconfig=~/.kube/config-k8s-cluster get svc -n alvs
kubectl --kubeconfig=~/.kube/config-k8s-cluster get svc -n monitoring
```

---

## Шаг 4: Проверка работоспособности

### 4.1. Проверка приложения

```bash
# Получение IP адресов узлов
kubectl --kubeconfig=~/.kube/config-k8s-cluster get nodes -o wide

# Доступ к приложению (замените <node-ip> на IP любого узла)
curl http://<node-ip>:30080

# Или через браузер
# http://192.168.1.92:30080
# http://192.168.1.93:30080
# http://192.168.1.94:30080
```

### 4.2. Проверка метрик

```bash
# Проверка метрик приложения
curl http://<node-ip>:30080/metrics

# Проверка Prometheus
curl http://<node-ip>:30090/api/v1/targets
```

### 4.3. Доступ к Grafana

1. Откройте в браузере: `http://<node-ip>:30300`
2. Логин: `admin`
3. Пароль: `admin`
4. Проверьте, что Prometheus добавлен как Data Source

### 4.4. Проверка логов

```bash
# Логи приложения
kubectl --kubeconfig=~/.kube/config-k8s-cluster logs -n alvs -l app=alvs-app --tail=50

# Логи PostgreSQL
kubectl --kubeconfig=~/.kube/config-k8s-cluster logs -n alvs -l app=postgres --tail=50
```

---

## Устранение проблем

### Проблема: Узлы не присоединяются к кластеру

**Решение:**
1. Проверьте, что команда join правильная
2. Проверьте сетевую связность между узлами
3. Проверьте, что порты открыты
4. Проверьте логи kubelet: `journalctl -u kubelet -f`

### Проблема: Поды не запускаются

**Решение:**
```bash
# Проверка описания пода
kubectl describe pod <pod-name> -n alvs

# Проверка событий
kubectl get events -n alvs --sort-by='.lastTimestamp'
```

### Проблема: PVC не создается

**Решение:**
1. Проверьте StorageClass: `kubectl get storageclass`
2. Если используется local-path, установите local-path-provisioner
3. Или используйте hostPath для разработки

### Проблема: Приложение не подключается к PostgreSQL

**Решение:**
1. Проверьте, что PostgreSQL под запущен: `kubectl get pods -n alvs -l app=postgres`
2. Проверьте секреты: `kubectl get secret postgres-secret -n alvs -o yaml`
3. Проверьте логи приложения на ошибки подключения

### Проблема: Prometheus не видит приложение

**Решение:**
1. Проверьте, что поды имеют правильные labels: `kubectl get pods -n alvs --show-labels`
2. Проверьте конфигурацию Prometheus: `kubectl get configmap prometheus-config -n monitoring -o yaml`
3. Проверьте targets в Prometheus UI

---

## Полезные команды

```bash
# Масштабирование приложения
kubectl scale deployment alvs-app -n alvs --replicas=3

# Перезапуск deployment
kubectl rollout restart deployment alvs-app -n alvs

# Просмотр ресурсов
kubectl top nodes
kubectl top pods -n alvs

# Удаление всего деплоя
kubectl delete namespace alvs monitoring

# Просмотр всех ресурсов в namespace
kubectl get all -n alvs
```

---

## Дополнительные ресурсы

- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Ansible Documentation](https://docs.ansible.com/)
- [Calico Networking](https://projectcalico.org/)
- [Prometheus Operator](https://prometheus-operator.dev/)

---

## Безопасность

⚠️ **Важно для production:**

1. Измените пароли в секретах
2. Используйте TLS для всех сервисов
3. Настройте Network Policies
4. Используйте RBAC для ограничения доступа
5. Регулярно обновляйте компоненты кластера
6. Настройте резервное копирование данных
