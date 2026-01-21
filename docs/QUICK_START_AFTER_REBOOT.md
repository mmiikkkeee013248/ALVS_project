# Быстрый старт после перезагрузки k8s-master

## Если VM зависла (soft lockup)

1. **Перезагрузите VM через VirtualBox** (Reset)
2. **Подождите 2-3 минуты** для полной загрузки
3. **Подключитесь по SSH:**
   ```bash
   ssh mike_1111@192.168.56.10
   ```

## Выполните скрипты по порядку

```bash
# Перейти в директорию со скриптами
cd ~/ALVS_project/config/kubernetes/scripts

# 1. Немедленная стабилизация (остановить все приложения)
chmod +x emergency-stabilize.sh
./emergency-stabilize.sh

# 2. Проверка окружения VM
chmod +x check-vm-settings.sh
./check-vm-settings.sh

# 3. Диагностика (найти причину)
chmod +x diagnose-lockup.sh
./diagnose-lockup.sh

# 4. Применить исправления (после анализа результатов)
chmod +x apply-fixes.sh
./apply-fixes.sh
```

## Ручные команды (если скрипты недоступны)

```bash
# Остановить все приложения
kubectl scale deployment -n alvs --all --replicas=0
kubectl scale deployment -n monitoring --all --replicas=0

# Проверить систему
uptime
free -h
sudo dmesg | tail -20

# Установить высокий приоритет для etcd
sudo ionice -c2 -n0 -p $(pgrep etcd)
```

## Проверка VirtualBox настроек (на Windows)

1. Откройте VirtualBox
2. Выберите `k8s-master` → Настройки
3. **Система → Ускорение → Паравиртуализация интерфейса:** должно быть **KVM**
4. **Система → Процессор:** должно быть **4+ ядер**

## После стабилизации

Только после того, как система стабилизировалась:
- Проверьте `kubectl get nodes` - все должны быть Ready
- Постепенно поднимайте приложения (по одному)
- Мониторьте нагрузку: `watch -n 1 'kubectl top nodes'`
