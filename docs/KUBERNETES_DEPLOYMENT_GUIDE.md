# Инструкция по развертыванию Kubernetes (Новая версия)

Эта инструкция описывает процесс развертывания стабильного кластера Kubernetes на виртуальных машинах Ubuntu, с учетом исправлений для слабого оборудования и сетевых особенностей.

## 1. Требования к ресурсам
Для стабильной работы Master-узла (k8s-master) необходимо выделить:
- **CPU**: Минимум 4 ядра (рекомендуется 6).
- **RAM**: Минимум 8 ГБ.
- **Диск**: Минимум 20 ГБ.
- **ОС**: Ubuntu 22.04 LTS.

## 2. Подготовка окружения (Ansible)
Все настройки автоматизированы через Ansible. Плейбуки находятся в папке `~/ALVS_project/config/kubernetes/ansible`.

### Основные исправления, внесенные в автоматизацию:
1. **Containerd**: Отключены `snapshot_annotations` (исправление ошибки ядра `overlayfs: idmapped layers are currently not supported`).
2. **Таймауты etcd**: Увеличены `heartbeat-interval` (250ms) и `election-timeout` (1250ms) для компенсации медленной работы диска.
3. **Лимиты API-сервера**: Отключен `APIPriorityAndFairness` и увеличены таймауты запуска.
4. **Сетевая связность**: Принудительная привязка Kubelet к внутренним IP-адресам (`192.168.56.x`) через `--node-ip`.

## 3. Порядок развертывания

### Шаг 1: Очистка старого состояния (если есть)
На всех узлах выполните:
```bash
sudo kubeadm reset -f
sudo rm -rf /etc/cni/net.d
sudo rm -rf $HOME/.kube
```

### Шаг 2: Запуск основного плейбука
Запустите развертывание с Master-узла (где установлен Ansible):
```bash
cd ~/ALVS_project/config/kubernetes/ansible/playbooks
ansible-playbook -i ../inventory/hosts.yml site.yml
```

*Примечание: Если воркеры не присоединились с первого раза, запустите отдельно:*
```bash
ansible-playbook -i ../inventory/hosts.yml 03-join-workers.yml
```

### Шаг 3: Развертывание хранилища и приложения
После того как все узлы перешли в статус `Ready`, примените манифесты:
```bash
cd ~/ALVS_project/config/kubernetes/manifests
# Установка системы хранения (обязательно)
kubectl apply -f https://raw.githubusercontent.com/rancher/local-path-provisioner/master/deploy/local-path-storage.yaml
kubectl patch storageclass local-path -p '{"metadata": {"annotations":{"storageclass.kubernetes.io/is-default-class":"true"}}}'

# Установка приложения
kubectl apply -f 00-namespace.yml
kubectl apply -f 01-postgres-secret.yml
kubectl apply -f 02-postgres-pvc.yml
kubectl apply -f 03-postgres-deployment.yml
kubectl apply -f 04-app-configmap.yml
kubectl apply -f 05-app-secret.yml
kubectl apply -f 06-app-deployment-fixed.yml
```

## 4. Проверка работоспособности
1. **Узлы**: `kubectl get nodes` (все 3 должны быть в статусе `Ready`).
2. **Поды**: `kubectl get pods -n alvs` (все должны быть `1/1 Running`).
3. **Доступ**: Приложение доступно по IP мастера: `http://192.168.100.103:30080`.

## 5. Решение проблем
- **API Server connection refused**: Подождите 1-2 минуты, пока API-сервер перезагрузится после применения тюнинга в манифестах.
- **Node NotReady**: Проверьте логи Kubelet (`journalctl -u kubelet`) и наличие сокета containerd (`/run/containerd/containerd.sock`).
- **Postgres Pending**: Проверьте наличие StorageClass по умолчанию (`kubectl get sc`) и статус пода `local-path-provisioner`.
