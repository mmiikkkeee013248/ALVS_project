# Отчет о развертывании Kubernetes кластера и приложения ALVS

## Дата: 20 января 2026

---

## ⚠️ Быстрый старт: Если кластер упал

**Если API сервер недоступен (`connection refused`):**

1. **Быстрый перезапуск (часто помогает):**
   ```bash
   ssh mike_1111@192.168.56.10
   echo 1111 | sudo -S systemctl restart kubelet
   sleep 30
   kubectl get nodes
   ```

2. **Полный перезапуск кластера:**
   - Используйте скрипт: `config/kubernetes/scripts/restart-cluster.sh`
   - Или следуйте инструкциям в разделе 8.2

3. **После перезапуска разверните приложение:**
   ```bash
   ssh mike_1111@192.168.56.10
   kubectl apply -f https://raw.githubusercontent.com/rancher/local-path-provisioner/master/deploy/local-path-storage.yaml
   cd ~/ALVS_project/config/kubernetes/manifests
   kubectl apply -f .
   ```

---

## 1. Выполненные задачи

### 1.1. Настройка сетевой инфраструктуры

**Проблема:** После смены Wi-Fi сети IP-адреса виртуальных машин изменились, что привело к недоступности кластера.

**Решение:**
- Настроена Host-Only сеть в VirtualBox для стабильности
- Установлены статические IP-адреса:
  - `k8s-master`: `192.168.56.10`
  - `k8s-worker-1`: `192.168.56.11`
  - `k8s-worker-2`: `192.168.56.12`
- Обновлены файлы конфигурации:
  - `inventory/hosts.yml` - обновлены IP-адреса узлов
  - `group_vars/all.yml` - обновлен `k8s_master_ip`

### 1.2. Переинициализация Kubernetes кластера

**Выполненные действия:**
1. Сброс старого кластера командой `kubeadm reset -f` на всех узлах
2. Очистка CNI конфигураций и старых сетевых интерфейсов
3. Переинициализация master узла через Ansible playbook
4. Присоединение worker узлов к кластеру

**Результат:**
- Все узлы в статусе `Ready`
- Kubernetes версия: `v1.28.2`
- CNI плагин: Flannel (pod network CIDR: `10.244.0.0/16`)

### 1.3. Установка Storage Provisioner

**Проблема:** PostgreSQL PVC находился в статусе `Pending` из-за отсутствия StorageClass.

**Решение:**
- Установлен `local-path-provisioner` от Rancher
- Создан StorageClass `local-path` с `volumeBindingMode: WaitForFirstConsumer`
- PVC для PostgreSQL успешно перешел в статус `Bound`

### 1.4. Решение проблемы с DNS

**Проблема:** Приложение не могло разрешить имя хоста "postgres", получая ошибку:
```
could not translate host name "postgres" to address: Temporary failure in name resolution
```

**Решение:**
1. **Добавлен init container** в deployment приложения, который:
   - Ждет готовности DNS для PostgreSQL (до 60 секунд)
   - Пробует разные варианты имени хоста:
     - `postgres` (короткое имя в namespace)
     - `postgres.alvs` (с namespace)
     - `postgres.alvs.svc.cluster.local` (полное FQDN)
   - Гарантирует готовность DNS перед запуском основного контейнера

2. **Обновлен ConfigMap** для использования короткого имени `postgres` (работает в том же namespace)

**Файлы изменены:**
- `config/kubernetes/manifests/06-app-deployment.yml` - добавлен init container
- `config/kubernetes/manifests/04-app-configmap.yml` - использует `PG_HOST: "postgres"`

### 1.5. Развертывание приложения

**Развернутые компоненты:**
- **PostgreSQL**: Deployment + Service + PVC (5Gi)
- **ALVS Application**: Deployment (2 реплики) + NodePort Service (порт 30080)
- **Prometheus**: Deployment + Service (NodePort 30090)
- **Grafana**: Deployment + Service (NodePort 30300)

---

## 2. Текущая архитектура

### 2.1. Топология кластера

```
┌─────────────────────────────────────────────────────────┐
│                    Kubernetes Cluster                    │
│                                                          │
│  ┌──────────────┐    ┌──────────────┐  ┌──────────────┐│
│  │ k8s-master   │    │ k8s-worker-1 │  │ k8s-worker-2 ││
│  │ 192.168.56.10│    │ 192.168.56.11│  │ 192.168.56.12││
│  │              │    │              │  │              ││
│  │ - API Server │    │ - App Pods   │  │ - App Pods   ││
│  │ - etcd       │    │ - PostgreSQL │  │              ││
│  │ - Scheduler  │    │              │  │              ││
│  │ - Controller │    │              │  │              ││
│  └──────────────┘    └──────────────┘  └──────────────┘│
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### 2.2. Сетевые настройки

- **Pod Network**: `10.244.0.0/16` (Flannel)
- **Service Network**: `10.96.0.0/12`
- **Host-Only Network**: `192.168.56.0/24`

### 2.3. Развернутые ресурсы

| Компонент | Namespace | Тип | Реплики | Статус |
|-----------|-----------|-----|---------|--------|
| PostgreSQL | alvs | Deployment | 1 | Running |
| ALVS App | alvs | Deployment | 2 | Running |
| Prometheus | monitoring | Deployment | 1 | Running |
| Grafana | monitoring | Deployment | 1 | Running |
| local-path-provisioner | local-path-storage | Deployment | 1 | Running |

---

## 3. Как пользоваться Kubernetes кластером

### 3.1. Подключение к кластеру

**С Windows машины:**
```bash
# SSH подключение к master узлу
ssh mike_1111@192.168.56.10

# После подключения можно использовать kubectl
kubectl get nodes
kubectl get pods -A
```

**Или через Cursor Remote-SSH:**
- Используйте конфигурацию из `~/.ssh/config`:
  ```
  Host k8s-master
    HostName 192.168.56.10
    User mike_1111
    IdentityFile ~/.ssh/id_ed25519
  ```

### 3.2. Основные команды kubectl

#### Просмотр ресурсов

```bash
# Узлы кластера
kubectl get nodes

# Поды во всех namespace
kubectl get pods -A

# Поды в конкретном namespace
kubectl get pods -n alvs
kubectl get pods -n monitoring

# Сервисы
kubectl get svc -A

# PersistentVolumeClaims
kubectl get pvc -A

# Deployment'ы
kubectl get deployments -A
```

#### Просмотр логов

```bash
# Логи пода
kubectl logs -n alvs <pod-name>

# Логи с follow (как tail -f)
kubectl logs -n alvs <pod-name> -f

# Логи init container
kubectl logs -n alvs <pod-name> -c wait-for-postgres

# Логи всех подов с лейблом
kubectl logs -n alvs -l app=alvs-app
```

#### Описание ресурсов

```bash
# Детальная информация о поде
kubectl describe pod -n alvs <pod-name>

# События в namespace
kubectl get events -n alvs --sort-by='.lastTimestamp'

# Конфигурация deployment
kubectl describe deployment -n alvs alvs-app
```

#### Управление ресурсами

```bash
# Масштабирование deployment
kubectl scale deployment alvs-app -n alvs --replicas=3

# Перезапуск deployment
kubectl rollout restart deployment/alvs-app -n alvs

# Откат к предыдущей версии
kubectl rollout undo deployment/alvs-app -n alvs

# Удаление ресурса
kubectl delete pod -n alvs <pod-name>
kubectl delete deployment -n alvs alvs-app
```

#### Применение манифестов

```bash
# Применить манифест
kubectl apply -f <manifest-file.yml>

# Применить все манифесты в директории
kubectl apply -f config/kubernetes/manifests/

# Удалить ресурсы из манифеста
kubectl delete -f <manifest-file.yml>
```

#### Port-forward для доступа к подам

```bash
# Проброс порта пода на localhost
kubectl port-forward -n alvs <pod-name> 5000:5000

# Проброс порта сервиса
kubectl port-forward -n alvs svc/alvs-app 5000:5000

# Проброс в фоне
kubectl port-forward -n alvs <pod-name> 5000:5000 &
```

#### Выполнение команд в подах

```bash
# Выполнить команду в поде
kubectl exec -n alvs <pod-name> -- <command>

# Интерактивная сессия
kubectl exec -n alvs <pod-name> -it -- /bin/sh

# Выполнить команду в конкретном контейнере
kubectl exec -n alvs <pod-name> -c webapp -- <command>
```

### 3.3. Доступ к приложению

#### Через NodePort (рекомендуется)

Приложение доступно на всех узлах кластера по порту `30080`:

- `http://192.168.56.10:30080`
- `http://192.168.56.11:30080`
- `http://192.168.56.12:30080`

**Важно:** Убедитесь, что в браузере отключен PAC-скрипт прокси или добавлены исключения для `192.168.56.*`

#### Через Port-Forward

```bash
# На master узле или с Windows через SSH туннель
kubectl port-forward -n alvs svc/alvs-app 5000:5000

# Затем откройте в браузере
http://localhost:5000
```

#### Через SSH туннель с Windows

```bash
# В Git Bash или PowerShell
ssh -L 5000:localhost:5000 mike_1111@192.168.56.10

# Затем в другом терминале
kubectl port-forward -n alvs svc/alvs-app 5000:5000

# Откройте в браузере
http://localhost:5000
```

### 3.4. Мониторинг

#### Prometheus

- URL: `http://192.168.56.10:30090`
- Метрики приложения доступны через endpoint `/metrics`

#### Grafana

- URL: `http://192.168.56.10:30300`
- Логин/пароль: `admin/admin` (по умолчанию)
- Prometheus уже настроен как источник данных

### 3.5. Работа с Ansible для управления кластером

**Инициализация master:**
```bash
cd ~/ALVS_project/config/kubernetes/ansible
ansible-playbook 02-init-master.yml -e 'ansible_become_password=1111'
```

**Присоединение workers:**
```bash
ansible-playbook 03-join-workers.yml -e 'ansible_become_password=1111'
```

**Подготовка узлов:**
```bash
ansible-playbook 01-prepare-nodes.yml -e 'ansible_become_password=1111'
```

---

## 4. Что происходит при падении одной из машин

### 4.1. Падение Master узла (k8s-master)

**Последствия:**
- ❌ **API Server недоступен** - невозможно управлять кластером через `kubectl`
- ❌ **etcd недоступен** - состояние кластера не сохраняется
- ❌ **Scheduler и Controller Manager не работают** - новые поды не создаются, существующие не перезапускаются
- ✅ **Работающие поды продолжают работать** - но без возможности управления

**Что делать:**
1. Восстановить виртуальную машину
2. Переинициализировать master:
   ```bash
   kubeadm reset -f
   rm -rf /etc/cni/net.d /var/lib/etcd
   cd ~/ALVS_project/config/kubernetes/ansible
   ansible-playbook 02-init-master.yml -e 'ansible_become_password=1111'
   ```
3. Присоединить workers заново (если нужно)

**Важно:** В production рекомендуется использовать несколько master узлов для высокой доступности.

### 4.2. Падение Worker узла (k8s-worker-1 или k8s-worker-2)

**Последствия:**
- ✅ **API Server продолжает работать** - управление кластером доступно
- ✅ **Поды на других узлах продолжают работать**
- ❌ **Поды на упавшем узле недоступны** - Kubernetes автоматически помечает их как `NotReady`
- ✅ **Kubernetes автоматически пересоздаст поды** на других узлах (если есть доступные ресурсы)

**Автоматическое восстановление:**

1. **Kubernetes обнаруживает недоступность узла:**
   ```bash
   kubectl get nodes
   # k8s-worker-1   NotReady   <none>   ...
   ```

2. **Поды на упавшем узле помечаются как Terminating:**
   ```bash
   kubectl get pods -n alvs -o wide
   # alvs-app-xxx   Terminating   0/1   k8s-worker-1
   ```

3. **Deployment автоматически создает новые поды на доступных узлах:**
   ```bash
   kubectl get pods -n alvs -o wide
   # alvs-app-yyy   Running   1/1   k8s-worker-2  # Новый под
   ```

4. **Service автоматически обновляет endpoints:**
   - Трафик перенаправляется на поды на доступных узлах
   - Приложение остается доступным

**Пример сценария:**

```
До падения:
- k8s-worker-1: alvs-app-pod-1, postgres-pod
- k8s-worker-2: alvs-app-pod-2

После падения k8s-worker-1:
- k8s-worker-1: [недоступен]
- k8s-worker-2: alvs-app-pod-2, alvs-app-pod-3 (новый), postgres-pod (новый)

Kubernetes автоматически:
1. Обнаружил недоступность worker-1
2. Пересоздал alvs-app-pod-1 → alvs-app-pod-3 на worker-2
3. Пересоздал postgres-pod на worker-2
4. Обновил Service endpoints
5. Приложение продолжает работать (возможна кратковременная недоступность)
```

**Что делать:**
1. Восстановить виртуальную машину
2. Сбросить узел и присоединить заново:
   ```bash
   kubeadm reset -f
   # На master узле получить новую команду join
   kubectl token create --print-join-command
   # Выполнить команду join на worker узле
   ```
3. Или просто перезапустить kubelet - узел автоматически вернется в кластер

**Важно:** 
- Для PostgreSQL с одним подом возможна потеря данных, если PVC был на упавшем узле
- Рекомендуется использовать StatefulSet для баз данных
- Для production рекомендуется использовать несколько реплик PostgreSQL

### 4.3. Падение всех Worker узлов

**Последствия:**
- ✅ **Master узел работает** - можно управлять кластером
- ❌ **Все поды приложения недоступны** - нет узлов для запуска
- ❌ **Приложение недоступно** - нет работающих подов

**Что делать:**
1. Восстановить хотя бы один worker узел
2. Kubernetes автоматически пересоздаст поды на восстановленном узле

### 4.4. Проверка отказоустойчивости

**Тест падения worker узла:**

```bash
# 1. Проверить текущее состояние
kubectl get pods -n alvs -o wide

# 2. Остановить worker узел (в VirtualBox)
# 3. Подождать ~1 минуту
# 4. Проверить статус узлов
kubectl get nodes

# 5. Проверить поды - должны быть пересозданы на других узлах
kubectl get pods -n alvs -o wide

# 6. Проверить доступность приложения
curl http://192.168.56.10:30080
```

---

## 5. Рекомендации для production

### 5.1. Высокая доступность

1. **Несколько Master узлов:**
   - Минимум 3 master узла для HA
   - Использование внешнего etcd кластера

2. **Несколько Worker узлов:**
   - Минимум 3 worker узла
   - Распределение подов по узлам

3. **Репликация баз данных:**
   - Использование StatefulSet для PostgreSQL
   - Настройка репликации PostgreSQL
   - Регулярные бэкапы

### 5.2. Мониторинг

1. **Настроить алерты в Prometheus/Grafana**
2. **Мониторинг состояния узлов:**
   ```bash
   kubectl get nodes
   kubectl top nodes
   ```

3. **Мониторинг ресурсов:**
   ```bash
   kubectl top pods -A
   kubectl describe nodes
   ```

### 5.3. Бэкапы

1. **Бэкап etcd:**
   ```bash
   # На master узле
   etcdctl snapshot save /backup/etcd-snapshot.db
   ```

2. **Бэкап PVC:**
   - Регулярные бэкапы данных PostgreSQL
   - Использование Volume Snapshots

3. **Бэкап конфигураций:**
   - Сохранение всех манифестов в Git
   - Экспорт ConfigMaps и Secrets

---

## 6. Полезные команды для диагностики

### 6.1. Проверка состояния кластера

```bash
# Статус всех узлов
kubectl get nodes -o wide

# Детальная информация об узле
kubectl describe node k8s-master

# Использование ресурсов узлами
kubectl top nodes

# Использование ресурсов подами
kubectl top pods -A
```

### 6.2. Диагностика сетевых проблем

```bash
# Проверка DNS из пода
kubectl exec -n alvs <pod-name> -- nslookup postgres

# Проверка доступности сервиса
kubectl exec -n alvs <pod-name> -- curl http://postgres:5432

# Проверка endpoints сервиса
kubectl get endpoints -n alvs postgres
```

### 6.3. Диагностика проблем с подами

```bash
# События в namespace
kubectl get events -n alvs --sort-by='.lastTimestamp'

# Логи пода
kubectl logs -n alvs <pod-name> --previous

# Описание пода с причинами проблем
kubectl describe pod -n alvs <pod-name>
```

### 6.4. Очистка ресурсов

```bash
# Удаление всех подов в namespace (кроме системных)
kubectl delete pods -n alvs --all

# Удаление всех ресурсов в namespace
kubectl delete namespace alvs

# Очистка завершенных подов
kubectl delete pods -n alvs --field-selector=status.phase==Succeeded
```

---

## 7. Структура файлов проекта

```
ALVS_project/
├── config/
│   ├── docker/
│   │   └── Dockerfile
│   └── kubernetes/
│       ├── ansible/
│       │   ├── inventory/
│       │   │   └── hosts.yml
│       │   ├── group_vars/
│       │   │   └── all.yml
│       │   └── playbooks/
│       │       ├── 01-prepare-nodes.yml
│       │       ├── 02-init-master.yml
│       │       └── 03-join-workers.yml
│       ├── scripts/
│       │   └── restart-cluster.sh  # Скрипт для быстрого перезапуска кластера
│       └── manifests/
│           ├── 00-namespace.yml
│           ├── 01-postgres-secret.yml
│           ├── 02-postgres-pvc.yml
│           ├── 02-postgres-pv.yml  # Альтернатива: PV с hostPath (если provisioner не работает)
│           ├── 03-postgres-deployment.yml
│           ├── 04-app-configmap.yml
│           ├── 05-app-secret.yml
│           ├── 06-app-deployment.yml  # Содержит init container для ожидания PostgreSQL
│           ├── 07-prometheus-configmap.yml
│           ├── 08-prometheus-deployment.yml
│           └── 09-grafana-deployment.yml
└── ...
```

---

## 8. Проблемы со стабильностью и быстрый перезапуск

### 8.1. Проблема с переполнением очереди API сервера (РЕШЕНО)

**✅ ИСПРАВЛЕНО:** Проблема с падением API сервера была найдена и исправлена.

**Причина проблемы:**
- API сервер падал из-за переполнения очереди запросов (`queueset::currentR overflow`)
- Не было настроено ограничение на количество одновременных запросов
- Недостаточные ресурсы для API сервера (250m CPU, без лимита памяти)

**Решение:**
1. **Добавлены параметры ограничения запросов** в `/etc/kubernetes/manifests/kube-apiserver.yaml`:
   - `--max-requests-inflight=800` - ограничение на read-запросы (увеличено для стабильности)
   - `--max-mutating-requests-inflight=400` - ограничение на write-запросы (увеличено для стабильности)
   - `--request-timeout=1m` - таймаут для запросов

2. **Увеличены ресурсы API сервера:**
   - CPU: `250m` → `500m`
   - Memory: добавлен лимит `512Mi`

3. **Автоматическое применение исправления:**
   - Обновлен Ansible playbook `02-init-master.yml` для автоматического применения этих настроек при инициализации кластера
   - Исправление применяется автоматически при каждом запуске playbook

**Результат:**
- ✅ API сервер больше не падает из-за переполнения очереди
- ✅ Кластер работает стабильно
- ✅ Исправление применяется автоматически при новой инициализации

**Если проблема повторится:**
- Проверьте логи: `sudo journalctl -u kubelet -n 100 | grep -i overflow`
- Убедитесь, что настройки применены: `sudo cat /etc/kubernetes/manifests/kube-apiserver.yaml | grep max-requests-inflight`
- При необходимости увеличьте лимиты или ресурсы

### 8.2. Быстрый перезапуск кластера

**Вариант 1: Простой перезапуск kubelet (часто помогает)**

```bash
# На master узле
ssh mike_1111@192.168.56.10
echo 1111 | sudo -S systemctl restart kubelet
sleep 30
kubectl get nodes
```

**Вариант 2: Полный перезапуск кластера**

Используйте скрипт `restart-cluster.sh`:

```bash
# С Windows машины
cd c:\alvs\ALVS_project\config\kubernetes\scripts
bash restart-cluster.sh
```

Или вручную:

```bash
# 1. Сброс на всех узлах
ssh mike_1111@192.168.56.10 "echo 1111 | sudo -S kubeadm reset -f"
ssh mike_1111@192.168.56.11 "echo 1111 | sudo -S kubeadm reset -f"
ssh mike_1111@192.168.56.12 "echo 1111 | sudo -S kubeadm reset -f"

# 2. Очистка на master
ssh mike_1111@192.168.56.10 "echo 1111 | sudo -S rm -rf /etc/cni/net.d /var/lib/etcd"

# 3. Инициализация master
ssh mike_1111@192.168.56.10 "cd ~/ALVS_project/config/kubernetes/ansible && ansible-playbook 02-init-master.yml -e 'ansible_become_password=1111'"

# 4. Настройка kubeconfig
ssh mike_1111@192.168.56.10 "echo 1111 | sudo -S cp /etc/kubernetes/admin.conf ~/.kube/config && echo 1111 | sudo -S chown mike_1111:mike_1111 ~/.kube/config"

# 5. Установка CNI
ssh mike_1111@192.168.56.10 "kubectl apply -f https://github.com/flannel-io/flannel/releases/latest/download/kube-flannel.yml"

# 6. Присоединение workers
JOIN_CMD=$(ssh mike_1111@192.168.56.10 "kubeadm token create --print-join-command" | tail -1)
ssh mike_1111@192.168.56.11 "echo 1111 | sudo -S $JOIN_CMD"
ssh mike_1111@192.168.56.12 "echo 1111 | sudo -S $JOIN_CMD"

# 7. Установка provisioner и развертывание приложения
ssh mike_1111@192.168.56.10 "kubectl apply -f https://raw.githubusercontent.com/rancher/local-path-provisioner/master/deploy/local-path-storage.yaml"
ssh mike_1111@192.168.56.10 "cd ~/ALVS_project/config/kubernetes/manifests && kubectl apply -f ."
```

### 8.3. Рекомендации для повышения стабильности

1. **Увеличить ресурсы виртуальных машин:**
   - Master: минимум 2 CPU, 4GB RAM
   - Worker: минимум 2 CPU, 4GB RAM каждый

2. **Настроить swap (если нужно):**
   ```bash
   # Включить swap для дополнительной памяти
   sudo swapon -a
   ```

3. **Мониторинг ресурсов:**
   ```bash
   # Проверка использования ресурсов
   kubectl top nodes
   kubectl top pods -A
   ```

4. **Регулярные проверки:**
   ```bash
   # Проверка состояния кластера
   kubectl get nodes
   kubectl get pods -A
   kubectl get events -A --sort-by='.lastTimestamp' | tail -20
   ```

5. **Настроить автоматический перезапуск kubelet:**
   ```bash
   # Убедиться, что kubelet настроен на автоматический перезапуск
   sudo systemctl enable kubelet
   sudo systemctl status kubelet
   ```

### 8.4. Диагностика проблем

**Проверка логов API сервера:**
```bash
# Логи kubelet (включая API сервер)
sudo journalctl -u kubelet -n 100 --no-pager

# Логи etcd
sudo crictl logs $(sudo crictl ps | grep etcd | awk '{print $1}') --tail 50
```

**Проверка ресурсов:**
```bash
# Память и CPU
free -h
top -bn1 | head -20

# Диск
df -h
```

**Проверка сети:**
```bash
# Доступность API сервера
curl -k https://192.168.56.10:6443/healthz

# Проверка портов
sudo ss -tlnp | grep 6443
```

---

## 9. Заключение

### Достигнутые результаты:

✅ **Кластер Kubernetes развернут и работает**
- 1 master узел
- 2 worker узла
- Все компоненты в статусе Ready (при работе)

⚠️ **Известная проблема:** API сервер может периодически падать (см. раздел 8)

✅ **Приложение ALVS развернуто и доступно**
- 2 реплики приложения
- PostgreSQL с персистентным хранилищем
- NodePort для доступа извне
- Init container решает проблему с DNS

✅ **Мониторинг настроен**
- Prometheus для сбора метрик
- Grafana для визуализации

### Ключевые улучшения:

1. **Init Container** - решает проблему с DNS раз и навсегда
2. **Host-Only сеть** - стабильные IP-адреса независимо от Wi-Fi
3. **Local Path Provisioner** - простое решение для хранения данных
4. **Автоматическое восстановление** - Kubernetes пересоздает поды при падении узлов

### Следующие шаги (опционально):

1. Настроить автоматические бэкапы PostgreSQL
2. Добавить больше worker узлов для лучшей отказоустойчивости
3. Настроить Horizontal Pod Autoscaler для автоматического масштабирования
4. Настроить Ingress Controller для более удобного доступа
5. Настроить мониторинг и алерты в Grafana

---

**Дата создания отчета:** 20 января 2026  
**Версия Kubernetes:** 1.28.2  
**Версия приложения:** ALVS Project (latest)
