# Ansible инфраструктура для DNS и NGINX

Этот проект содержит Ansible playbook и роли для развертывания DNS сервера (Bind9) и веб-сервера NGINX с поддержкой двух FQDN (A-запись и CNAME-алиас).

## Структура проекта

```
ansible/
├── ansible.cfg              # Конфигурация Ansible
├── playbook.yml             # Главный playbook
├── inventory/
│   └── hosts                # Inventory файл с хостами
├── group_vars/
│   ├── all.yml              # Общие переменные
│   ├── dns_servers.yml      # Переменные для DNS сервера
│   └── web_servers.yml      # Переменные для веб-серверов
└── roles/
    ├── dns/                 # Роль для DNS сервера (Bind9)
    └── nginx/               # Роль для NGINX
```

## Подготовка

### 1. Подготовка сервера Ansible

На машине, которая будет управлять другими, выполните:

```bash
sudo ./init_ansi.sh
```

Скрипт установит:
- Ansible
- Необходимые пакеты (python3, openssh-server, bind9-dnsutils, dnsutils)
- Настроит SSH
- Создаст структуру директорий

### 2. Подготовка клиентских машин

**Важно:** Ansible — безагентная система управления конфигурациями. Это означает, что **не требуется установка дополнительного ПО на клиентских машинах**. 

Взаимодействие происходит через SSH-соединение:
- Ansible сервер подключается к клиентам по SSH
- На клиентах используется стандартный Python (обычно уже установлен в Ubuntu/Debian)
- Модули Ansible передаются по SSH и выполняются временно на клиентах
- После выполнения модули автоматически удаляются

**Минимальные требования на клиентах:**
- Python 3.6+ (обычно уже установлен)
- SSH сервер (обычно уже установлен)
- Доступ по SSH с правами root или пользователя с sudo

Если Python отсутствует, Ansible может установить его автоматически при первом подключении (требуется настройка в ansible.cfg).

### 3. Настройка inventory

Отредактируйте файл `inventory/hosts` и укажите реальные IP адреса:

```ini
[dns_servers]
server ansible_host=192.168.1.10 ansible_user=root

[web_servers]
server ansible_host=192.168.1.10 ansible_user=root

[clients]
client1 ansible_host=192.168.1.11 ansible_user=root
client2 ansible_host=192.168.1.12 ansible_user=root
```

### 4. Настройка переменных

Отредактируйте файлы в `group_vars/`:

- `dns_servers.yml` - настройки DNS (домен, FQDN, CNAME)
- `web_servers.yml` - настройки NGINX (порты, директории, FQDN)

### 5. Настройка SSH ключей (рекомендуется)

Для удобства работы скопируйте SSH ключи на клиентов (это позволит избежать ввода пароля при каждом подключении):

```bash
ssh-copy-id root@IP_CLIENT1
ssh-copy-id root@IP_CLIENT2
```

**Проверка подключения:**

Проверьте, что Ansible может подключиться ко всем хостам:

```bash
cd infrastructure/ansible
ansible all -i inventory/hosts -m ping
```

Если подключение успешно, вы увидите ответ `pong` от каждого хоста. Это означает, что:
- SSH соединение работает
- Python доступен на клиентах
- Ansible готов к работе

## Использование

### Базовая установка DNS

```bash
cd ansible
ansible-playbook playbook.yml --tags "untagged"
```

Это установит Bind9 и создаст базовую зону DNS.

### Добавление CNAME записи

1. Убедитесь, что в `group_vars/dns_servers.yml` определены переменные:
   ```yaml
   dns_cname_alias: "alias.example.local"
   dns_cname_target: "server.example.local"
   ```

2. Запустите playbook с тегом `my_dns`:
   ```bash
   ansible-playbook playbook.yml --tags "untagged,my_dns"
   ```

Это добавит CNAME запись в зону DNS и проверит разрешение обоих FQDN.

### Установка NGINX

```bash
ansible-playbook playbook.yml --tags "nginx"
```

Это установит NGINX и создаст конфигурации для обоих сайтов (A-запись и CNAME).

## Проверка

### Проверка DNS

```bash
# Проверка статуса сервиса
systemctl status bind9

# Проверка конфигурации
named-checkconf
named-checkzone example.local /etc/bind/zones/db.example.local

# Проверка разрешения FQDN
dig @IP_DNS_SERVER server.example.local
dig @IP_DNS_SERVER alias.example.local
```

### Проверка NGINX

```bash
# Проверка статуса сервиса
systemctl status nginx

# Проверка конфигурации
nginx -t

# Проверка в браузере
# http://server.example.local
# http://alias.example.local
```

Оба сайта должны отображать разные HTML страницы с разными цветами фона.

## Теги

Playbook использует следующие теги:

- `untagged` - базовые задачи DNS (установка, создание зоны)
- `my_dns` - добавление CNAME записи и проверка разрешения
- `nginx` - развертывание NGINX с конфигурацией для обоих FQDN

## Требования

- Ansible 2.9+
- Python 3.6+
- Ubuntu/Debian на целевых хостах
- Доступ по SSH с правами root

## Важные замечания

1. После настройки рекомендуется:
   - Отключить парольную аутентификацию SSH
   - Настроить SSH ключи
   - Отключить root-вход по SSH (создать обычного пользователя)

2. Убедитесь, что порты 53 (DNS), 80 (HTTP), 443 (HTTPS) открыты в файрволе

3. Для работы DNS убедитесь, что на клиентах настроен правильный DNS сервер
