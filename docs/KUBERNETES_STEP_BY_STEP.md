# Пошаговая инструкция развертывания Kubernetes кластера

Этот документ описывает каждый шаг развертывания и объясняет, зачем он нужен.

---

## Шаг 1: Проверка и установка Ansible

**Команда:**
```bash
ansible --version
```

**Зачем:** Ansible - это инструмент для автоматизации развертывания. Он позволяет выполнять команды на удаленных серверах без необходимости вручную подключаться к каждому.

**Что делает:** Проверяет, установлен ли Ansible. Если нет - устанавливает его через pip.

**Ожидаемый вывод:**
```
ansible [core 2.15.x]
  python version = 3.x.x
```

---

## Шаг 2: Проверка SSH подключения

**Команда:**
```bash
cd config/kubernetes/ansible
ansible all -i inventory/hosts.yml -m ping
```

**Зачем:** Убедиться, что управляющая машина может подключиться ко всем узлам кластера по SSH.

**Что делает:** Отправляет ping команду на все узлы из inventory файла.

**Ожидаемый вывод:**
```
k8s-master | SUCCESS => {
    "changed": false,
    "ping": "pong"
}
k8s-worker-1 | SUCCESS => {
    "changed": false,
    "ping": "pong"
}
k8s-worker-2 | SUCCESS => {
    "changed": false,
    "ping": "pong"
}
```

---

## Шаг 3: Подготовка узлов

**Команда:**
```bash
ansible-playbook -i inventory/hosts.yml playbooks/01-prepare-nodes.yml
```

**Зачем:** Подготовить все узлы (master и workers) для работы с Kubernetes. Все узлы должны иметь одинаковую конфигурацию.

**Что делает playbook:**
1. **Обновление системы** - устанавливает последние обновления безопасности
2. **Установка необходимых пакетов** - curl, apt-transport-https, ca-certificates
3. **Отключение swap** - Kubernetes требует отключенный swap для корректной работы
4. **Загрузка модулей ядра** - overlay и br_netfilter для работы контейнеров
5. **Настройка sysctl** - включает IP forwarding и настройки для сетевых мостов
6. **Установка containerd** - контейнерный runtime для Kubernetes
7. **Установка Kubernetes компонентов:**
   - `kubelet` - агент на каждом узле, который управляет подами
   - `kubeadm` - инструмент для инициализации кластера
   - `kubectl` - CLI для управления кластером

**Ожидаемый вывод:**
```
PLAY [Подготовка всех узлов Kubernetes кластера] ****************

TASK [Обновление списка пакетов] ******************************
ok: [k8s-master]
ok: [k8s-worker-1]
ok: [k8s-worker-2]

TASK [Установка необходимых пакетов] **************************
changed: [k8s-master]
changed: [k8s-worker-1]
changed: [k8s-worker-2]

... (много задач)

PLAY RECAP *****************************************************
k8s-master              : ok=15   changed=10   unreachable=0    failed=0
k8s-worker-1            : ok=15   changed=10   unreachable=0    failed=0
k8s-worker-2            : ok=15   changed=10   unreachable=0    failed=0
```

---

## Шаг 4: Инициализация master узла

**Команда:**
```bash
ansible-playbook -i inventory/hosts.yml playbooks/02-init-master.yml
```

**Зачем:** Создать Kubernetes кластер, инициализировав control plane на master узле.

**Что делает playbook:**
1. **Проверяет, не инициализирован ли уже кластер** - чтобы не переинициализировать
2. **Запускает `kubeadm init`** - создает кластер со следующими параметрами:
   - `--pod-network-cidr=10.244.0.0/16` - сеть для подов (Calico)
   - `--apiserver-advertise-address=192.168.1.92` - IP адрес master
   - `--service-cidr=10.96.0.0/12` - сеть для сервисов
3. **Создает kubeconfig** - файл конфигурации для kubectl
4. **Копирует kubeconfig для пользователя** - чтобы можно было использовать kubectl
5. **Устанавливает Calico CNI** - сетевой плагин для связи между подами
6. **Генерирует команду join** - для присоединения worker узлов

**Ожидаемый вывод:**
```
TASK [Инициализация Kubernetes кластера] **********************
changed: [k8s-master]

TASK [Установка CNI плагина (Calico)] *************************
changed: [k8s-master]

TASK [Получение команды join для worker узлов] ***************
ok: [k8s-master]

TASK [Вывод команды join] *************************************
ok: [k8s-master] => {
    "msg": "Команда для присоединения worker узлов: kubeadm join 192.168.1.92:6443 --token ..."
}
```

**Важно:** Сохраните команду join, она понадобится для следующего шага.

---

## Шаг 5: Присоединение worker узлов

**Команда:**
```bash
ansible-playbook -i inventory/hosts.yml playbooks/03-join-workers.yml
```

**Зачем:** Добавить worker узлы в кластер, чтобы они могли запускать поды приложения.

**Что делает playbook:**
1. **Получает команду join с master узла** - либо из файла, либо генерирует новую
2. **Проверяет, не присоединен ли уже узел** - чтобы не присоединять дважды
3. **Выполняет `kubeadm join`** на каждом worker узле - подключает их к кластеру

**Ожидаемый вывод:**
```
TASK [Присоединение worker узла к кластеру] *******************
changed: [k8s-worker-1]
changed: [k8s-worker-2]

TASK [Вывод результата присоединения] ************************
ok: [k8s-worker-1] => {
    "msg": [
        "This node has joined the cluster:"
    ]
}
```

---

## Шаг 6: Проверка кластера

**Команда:**
```bash
ssh -i ~/.ssh/id_ed25519 mike_1111@192.168.1.92 'kubectl get nodes -o wide'
```

**Зачем:** Убедиться, что все узлы успешно присоединились и находятся в состоянии Ready.

**Что делает:** Выполняет команду kubectl на master узле для проверки статуса всех узлов.

**Ожидаемый вывод:**
```
NAME           STATUS   ROLES           AGE   VERSION   INTERNAL-IP    EXTERNAL-IP
k8s-master     Ready    control-plane   5m    v1.28.0   192.168.1.92   <none>
k8s-worker-1   Ready    <none>          2m    v1.28.0   192.168.1.93   <none>
k8s-worker-2   Ready    <none>          2m    v1.28.0   192.168.1.94   <none>
```

**Статус Ready означает:**
- Узел подключен к кластеру
- Все системные компоненты работают
- Узел готов принимать поды

---

## Шаг 7: Копирование kubeconfig

**Команда:**
```bash
mkdir -p ~/.kube
scp -i ~/.ssh/id_ed25519 mike_1111@192.168.1.92:~/.kube/config ~/.kube/config-k8s-cluster
```

**Зачем:** Получить доступ к кластеру с управляющей машины для деплоя приложения.

**Что делает:** Копирует файл конфигурации Kubernetes с master узла на локальную машину.

**kubeconfig содержит:**
- Адрес API сервера
- Сертификаты для аутентификации
- Контекст кластера

---

## Шаг 8: Деплой приложения

**Команда:**
```bash
export KUBECONFIG=~/.kube/config-k8s-cluster
kubectl apply -f manifests/
```

**Зачем:** Развернуть все компоненты приложения в кластере.

**Что делает:** Применяет все Kubernetes манифесты в правильном порядке:

1. **00-namespace.yml** - создает namespace `alvs` и `monitoring`
2. **01-postgres-secret.yml** - создает секрет с паролем PostgreSQL
3. **02-postgres-pvc.yml** - создает PersistentVolumeClaim для данных БД
4. **03-postgres-deployment.yml** - разворачивает PostgreSQL
5. **04-app-configmap.yml** - создает ConfigMap с настройками приложения
6. **05-app-secret.yml** - создает секрет с паролем для подключения к БД
7. **06-app-deployment.yml** - разворачивает Flask приложение (2 реплики)
8. **07-09** - разворачивает Prometheus и Grafana

**Ожидаемый вывод:**
```
namespace/alvs created
namespace/monitoring created
secret/postgres-secret created
persistentvolumeclaim/postgres-pvc created
deployment.apps/postgres created
service/postgres created
configmap/app-config created
secret/app-secret created
deployment.apps/alvs-app created
service/alvs-app created
...
```

---

## Шаг 9-10: Ожидание готовности подов

**Команды:**
```bash
kubectl wait --for=condition=ready pod -l app=postgres -n alvs --timeout=300s
kubectl wait --for=condition=ready pod -l app=alvs-app -n alvs --timeout=300s
```

**Зачем:** Убедиться, что все поды успешно запустились и готовы принимать трафик.

**Что делает:** Ожидает, пока поды перейдут в состояние Ready. Это означает:
- Контейнер запущен
- Health checks пройдены
- Под готов к работе

**Ожидаемый вывод:**
```
pod/postgres-xxxxxxxxxx-xxxxx condition met
pod/alvs-app-xxxxxxxxxx-xxxxx condition met
pod/alvs-app-xxxxxxxxxx-yyyyy condition met
```

---

## Шаг 11-12: Проверка статуса

**Команды:**
```bash
kubectl get all -n alvs
kubectl get all -n monitoring
```

**Зачем:** Проверить, что все ресурсы созданы и работают корректно.

**Что показывает:**
- **Pods** - контейнеры приложения
- **Deployments** - управление репликами
- **Services** - сетевые точки доступа
- **ReplicaSets** - управление репликами подов

**Ожидаемый вывод:**
```
NAME                           READY   STATUS    RESTARTS   AGE
pod/alvs-app-xxxxx-xxxxx       1/1     Running   0          2m
pod/alvs-app-xxxxx-yyyyy       1/1     Running   0          2m
pod/postgres-xxxxx-xxxxx        1/1     Running   0          3m

NAME                      READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/alvs-app  2/2     2            2           2m
deployment.apps/postgres  1/1     1            1           3m

NAME              TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)          AGE
service/alvs-app  NodePort    10.96.xxx.xxx   <none>        5000:30080/TCP   2m
service/postgres  ClusterIP   10.96.yyy.yyy   <none>        5432/TCP         3m
```

---

## Шаг 13-14: Проверка сервисов

**Команды:**
```bash
kubectl get svc -n alvs -o wide
kubectl get svc -n monitoring -o wide
```

**Зачем:** Проверить сетевую конфигурацию и порты доступа к сервисам.

**Что показывает:**
- **ClusterIP** - внутренний IP сервиса в кластере
- **NodePort** - порт для доступа снаружи кластера
- **Endpoints** - IP адреса подов, обслуживающих сервис

**Ожидаемый вывод:**
```
NAME         TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)          NODE
alvs-app     NodePort    10.96.123.45    <none>        5000:30080/TCP   <all>
postgres     ClusterIP   10.96.67.89     <none>        5432/TCP        <all>

NAME         TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)          NODE
prometheus   NodePort    10.96.111.222   <none>        9090:30090/TCP   <all>
grafana      NodePort    10.96.333.444   <none>        3000:30300/TCP   <all>
```

**NodePort означает:**
- Сервис доступен на всех узлах кластера
- Порт 30080 → приложение (внутри контейнера 5000)
- Порт 30090 → Prometheus (внутри контейнера 9090)
- Порт 30300 → Grafana (внутри контейнера 3000)

---

## Шаг 15-16: Просмотр логов

**Команды:**
```bash
kubectl logs -n alvs -l app=alvs-app --tail=20
kubectl logs -n alvs -l app=postgres --tail=10
```

**Зачем:** Проверить, что приложение запустилось без ошибок и работает корректно.

**Что показывает:**
- Логи запуска приложения
- Ошибки подключения к БД (если есть)
- Информационные сообщения

**Ожидаемый вывод для приложения:**
```
[INFO] Загружено контактов: 0
[INFO] Приложение запущено на порту 5000
```

**Ожидаемый вывод для PostgreSQL:**
```
PostgreSQL init process complete; ready for start up.
database system is ready to accept connections
```

---

## Шаг 17: Получение IP адресов

**Команда:**
```bash
kubectl get nodes -o wide
```

**Зачем:** Узнать IP адреса узлов для доступа к приложению.

**Ожидаемый вывод:**
```
NAME           STATUS   ROLES           INTERNAL-IP    EXTERNAL-IP
k8s-master     Ready    control-plane   192.168.1.92   <none>
k8s-worker-1   Ready    <none>          192.168.1.93   <none>
k8s-worker-2   Ready    <none>          192.168.1.94   <none>
```

---

## Проверка работоспособности

После завершения всех шагов проверьте доступность:

```bash
# Приложение
curl http://192.168.1.92:30080
curl http://192.168.1.93:30080
curl http://192.168.1.94:30080

# Метрики
curl http://192.168.1.92:30080/metrics

# Prometheus
curl http://192.168.1.92:30090/api/v1/targets
```

---

## Заключение

После выполнения всех шагов у вас будет:

✅ **Kubernetes кластер** из 3 узлов  
✅ **PostgreSQL база данных** в StatefulSet  
✅ **Flask приложение** с 2 репликами  
✅ **Prometheus** для сбора метрик  
✅ **Grafana** для визуализации  

Все компоненты доступны через NodePort на любом узле кластера.
