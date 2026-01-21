# Диагностика и исправление CPU Soft Lockups на k8s-master

## Текущая ситуация

ВМ `k8s-master` полностью зависла из-за **kernel soft lockup**:
- CPU#0 застрял на 46-52 секунды
- RCU (Read-Copy-Update) kthread голодает
- Система не отвечает на SSH

## Немедленные действия

### Шаг 1: Перезагрузка VM
1. Откройте VirtualBox
2. Выберите `k8s-master`
3. Нажмите **"Reset"** (жесткая перезагрузка)

### Шаг 2: После загрузки - немедленно остановить все приложения

Подключитесь по SSH и выполните:

```bash
ssh mike_1111@192.168.56.10

# Остановить все приложения
kubectl scale deployment -n alvs --all --replicas=0
kubectl scale deployment -n monitoring --all --replicas=0
kubectl scale deployment -n local-path-storage --all --replicas=0

# Проверить, что API сервер стабилен
for i in {1..5}; do kubectl get nodes && echo "OK" || echo "FAIL"; sleep 2; done
```

## Phase 2: Проверка окружения виртуализации

### 2.1. Проверка Windows (хост)
- Откройте **Диспетчер задач** → вкладка **"Производительность"**
- Проверьте:
  - **CPU**: не должен быть на 100%
  - **Диск**: задержки записи должны быть < 50ms
  - **Память**: должно быть свободно минимум 2GB

### 2.2. Настройки VirtualBox для k8s-master

**Система → Процессор:**
- ✅ Количество процессоров: **4** (уже настроено)
- ✅ Включить PAE/NX: **включено**

**Система → Ускорение:**
- ✅ Включить VT-x/AMD-V: **включено**
- ✅ Включить вложенную виртуализацию: **включено**
- ✅ Паравиртуализация интерфейса: **KVM** (должно быть KVM, не Default)

**Если не KVM:**
1. Остановите VM
2. Система → Ускорение → Паравиртуализация интерфейса → **KVM**
3. Запустите VM

### 2.3. Проверка IO задержек внутри VM

После перезагрузки выполните:

```bash
# Установить sysstat если нет
sudo apt-get update && sudo apt-get install -y sysstat

# Проверить задержки диска
iostat -x 1 5

# Обратите внимание на колонки:
# - %util (должно быть < 80%)
# - await (должно быть < 50ms)
```

## Phase 3: Глубокая диагностика ядра

### 3.1. Мониторинг логов ядра

```bash
# В реальном времени
sudo journalctl -kf

# Или через dmesg
sudo dmesg -w
```

**Что искать:**
- Повторяющиеся `soft lockup` на одном и том же CPU
- Упоминания конкретных процессов (etcd, kubelet, containerd)
- Сообщения о нехватке памяти или swap

### 3.2. Проверка etcd (главный потребитель ресурсов)

```bash
# Найти PID etcd
ETCD_PID=$(pgrep -f etcd)

# Установить высокий приоритет IO для etcd
sudo ionice -c2 -n0 -p $ETCD_PID

# Проверить текущий приоритет
sudo ionice -p $ETCD_PID
```

### 3.3. Проверка переключений контекста и прерываний

```bash
# Установить sysstat если нет
sudo apt-get install -y sysstat

# Проверить переключения контекста
vmstat 1 5

# Обратите внимание на:
# - cs (context switches) - должно быть < 10000/sec
# - in (interrupts) - должно быть стабильно
```

## Phase 4: Исправления

### 4.1. Если проблема в диске (высокий await в iostat)

**Вариант A: Переместить VM на SSD**
- В VirtualBox: Настройки → Общие → Папка по умолчанию → указать путь на SSD

**Вариант B: Уменьшить логирование**
```bash
# Ограничить размер логов journald
sudo sed -i 's/#SystemMaxUse=/SystemMaxUse=500M/' /etc/systemd/journald.conf
sudo systemctl restart systemd-journald
```

### 4.2. Если проблема в CPU (высокая нагрузка)

**Вариант A: Увеличить ядра CPU в VirtualBox**
- Система → Процессор → увеличить до 6-8 ядер (если хост позволяет)

**Вариант B: Дополнительно ограничить API сервер**
```bash
# Уже применено в манифесте:
# --max-requests-inflight=800
# --max-mutating-requests-inflight=400
# --request-timeout=1m

# Можно снизить еще больше:
sudo sed -i 's/--max-requests-inflight=800/--max-requests-inflight=400/' /etc/kubernetes/manifests/kube-apiserver.yaml
sudo sed -i 's/--max-mutating-requests-inflight=400/--max-mutating-requests-inflight=200/' /etc/kubernetes/manifests/kube-apiserver.yaml
```

### 4.3. Оптимизация etcd (если он виновник)

```bash
# Установить высокий приоритет для etcd постоянно
# Создать systemd override
sudo mkdir -p /etc/systemd/system/etcd.service.d/
sudo tee /etc/systemd/system/etcd.service.d/override.conf <<EOF
[Service]
IOSchedulingClass=realtime
IOSchedulingPriority=0
CPUSchedulingPolicy=fifo
CPUSchedulingPriority=99
EOF

# Но это требует перезагрузки etcd, что сложно в kubeadm setup
# Лучше просто установить ionice при старте (через Ansible)
```

## Автоматические скрипты

Созданы скрипты для выполнения всех фаз диагностики:

### После перезагрузки VM - немедленно:

```bash
# 1. Стабилизация (Phase 1)
chmod +x ~/ALVS_project/config/kubernetes/scripts/emergency-stabilize.sh
~/ALVS_project/config/kubernetes/scripts/emergency-stabilize.sh

# 2. Проверка окружения VM (Phase 2)
chmod +x ~/ALVS_project/config/kubernetes/scripts/check-vm-settings.sh
~/ALVS_project/config/kubernetes/scripts/check-vm-settings.sh

# 3. Глубокая диагностика (Phase 3)
chmod +x ~/ALVS_project/config/kubernetes/scripts/diagnose-lockup.sh
~/ALVS_project/config/kubernetes/scripts/diagnose-lockup.sh

# 4. Применение исправлений (Phase 4)
chmod +x ~/ALVS_project/config/kubernetes/scripts/apply-fixes.sh
~/ALVS_project/config/kubernetes/scripts/apply-fixes.sh
```

## Рекомендации для предотвращения

1. **Никогда не запускайте все приложения одновременно** - поднимайте по одному
2. **Мониторьте нагрузку:** `watch -n 1 'kubectl top nodes'`
3. **Используйте ресурсные лимиты** для всех подов
4. **Рассмотрите увеличение ресурсов VM** до 6-8GB RAM и 6 CPU
