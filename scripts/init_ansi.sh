#!/bin/bash
# Скрипт для подготовки сервера Ansible (master node)
# Использование: sudo ./init_ansi.sh

set -e

echo "=== Подготовка сервера Ansible ==="

# Проверка прав root
if [ "$EUID" -ne 0 ]; then 
    echo "Ошибка: Скрипт должен быть запущен с правами root (sudo)"
    exit 1
fi

# Определение версии ОС
if [ -f /etc/os-release ]; then
    . /etc/os-release
    echo "Обнаружена система: $NAME $VERSION"
else
    echo "Предупреждение: Не удалось определить версию ОС"
fi

# Обновление списка пакетов
echo ""
echo "=== Обновление списка пакетов ==="
apt-get update

# Установка базовых пакетов
echo ""
echo "=== Установка базовых пакетов ==="
apt-get install -y \
    python3 \
    python3-pip \
    python3-dev \
    openssh-server \
    curl \
    wget \
    git \
    vim \
    net-tools \
    bind9-dnsutils \
    dnsutils \
    sshpass

# Установка Ansible
echo ""
echo "=== Установка Ansible ==="
if ! command -v ansible &> /dev/null; then
    apt-get install -y ansible
else
    echo "Ansible уже установлен"
fi

# Установка Python модулей для Ansible
echo ""
echo "=== Установка Python модулей для Ansible ==="
pip3 install --upgrade pip
pip3 install \
    setuptools \
    wheel \
    jinja2 \
    pyyaml

# Настройка SSH сервера
echo ""
echo "=== Настройка SSH сервера ==="
if [ ! -f /etc/ssh/sshd_config.backup ]; then
    cp /etc/ssh/sshd_config /etc/ssh/sshd_config.backup
    echo "Создана резервная копия конфигурации SSH"
fi

# Включение парольной аутентификации (для начальной настройки)
sed -i 's/#PasswordAuthentication yes/PasswordAuthentication yes/' /etc/ssh/sshd_config
sed -i 's/PasswordAuthentication no/PasswordAuthentication yes/' /etc/ssh/sshd_config

# Разрешение root-входа по SSH (опционально, для начальной настройки)
if ! grep -q "^PermitRootLogin" /etc/ssh/sshd_config; then
    echo "PermitRootLogin yes" >> /etc/ssh/sshd_config
elif grep -q "^PermitRootLogin no" /etc/ssh/sshd_config; then
    sed -i 's/^PermitRootLogin no/PermitRootLogin yes/' /etc/ssh/sshd_config
fi

# Запуск и включение SSH
systemctl enable ssh
systemctl enable sshd 2>/dev/null || true
systemctl restart ssh
systemctl restart sshd 2>/dev/null || true

# Проверка статуса SSH
if systemctl is-active --quiet ssh || systemctl is-active --quiet sshd; then
    echo "SSH сервер запущен и включен"
else
    echo "Предупреждение: Не удалось запустить SSH сервер"
fi

# Генерация SSH ключей (если отсутствуют)
echo ""
echo "=== Проверка SSH ключей ==="
if [ ! -f ~/.ssh/id_rsa ]; then
    echo "Генерация SSH ключей..."
    ssh-keygen -t rsa -b 4096 -f ~/.ssh/id_rsa -N "" -q
    echo "SSH ключи сгенерированы"
else
    echo "SSH ключи уже существуют"
fi

# Настройка файрвола (если установлен ufw)
if command -v ufw &> /dev/null; then
    echo ""
    echo "=== Настройка файрвола ==="
    ufw allow 22/tcp
    ufw allow 53/tcp
    ufw allow 53/udp
    ufw allow 80/tcp
    ufw allow 443/tcp
    echo "Правила файрвола добавлены (порты: 22, 53, 80, 443)"
fi

# Создание структуры директорий для Ansible
echo ""
echo "=== Создание структуры директорий Ansible ==="
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ANSIBLE_DIR="$PROJECT_ROOT/infrastructure/ansible"

mkdir -p "$ANSIBLE_DIR"/{inventory,group_vars,roles/{dns,nginx}/{defaults,tasks,handlers,templates,files}}
mkdir -p /var/www
chmod 755 /var/www

echo "Структура директорий создана в $ANSIBLE_DIR"

# Получение информации о системе
echo ""
echo "=== Информация о системе ==="
HOSTNAME=$(hostname)
FQDN=$(hostname -f 2>/dev/null || echo "$HOSTNAME")
IP_ADDRESS=$(ip route get 8.8.8.8 2>/dev/null | awk '{print $7; exit}' || hostname -I | awk '{print $1}')

echo "Имя хоста: $HOSTNAME"
echo "FQDN: $FQDN"
echo "IP адрес: $IP_ADDRESS"

# Проверка доступности пакетов для будущей установки
echo ""
echo "=== Проверка доступности пакетов ==="
PACKAGES=("bind9" "nginx")
for pkg in "${PACKAGES[@]}"; do
    if apt-cache show "$pkg" &> /dev/null; then
        echo "✓ Пакет $pkg доступен в репозиториях"
    else
        echo "✗ Пакет $pkg НЕ найден в репозиториях"
    fi
done

# Функция проверки статуса сервиса
check_service_status() {
    local service=$1
    if systemctl is-active --quiet "$service" 2>/dev/null; then
        echo "✓ Сервис $service запущен"
        return 0
    else
        echo "✗ Сервис $service не запущен"
        return 1
    fi
}

# Функция проверки разрешения FQDN
check_dns_resolution() {
    local fqdn=$1
    local dns_server=${2:-"127.0.0.1"}
    
    if command -v dig &> /dev/null; then
        result=$(dig @"$dns_server" "$fqdn" +short 2>/dev/null | head -n1)
        if [ -n "$result" ] && [ "$result" != ";" ]; then
            echo "✓ FQDN $fqdn разрешается в: $result"
            return 0
        else
            echo "✗ FQDN $fqdn не разрешается"
            return 1
        fi
    elif command -v host &> /dev/null; then
        result=$(host "$fqdn" "$dns_server" 2>/dev/null | grep "has address" | awk '{print $4}')
        if [ -n "$result" ]; then
            echo "✓ FQDN $fqdn разрешается в: $result"
            return 0
        else
            echo "✗ FQDN $fqdn не разрешается"
            return 1
        fi
    else
        echo "⚠ Утилиты dig и host не найдены, проверка DNS пропущена"
        return 2
    fi
}

# Функция проверки конфигурации NGINX
check_nginx_config() {
    if command -v nginx &> /dev/null; then
        if nginx -t 2>&1 | grep -q "successful"; then
            echo "✓ Конфигурация NGINX корректна"
            return 0
        else
            echo "✗ Ошибка в конфигурации NGINX"
            nginx -t 2>&1 | grep -i error || true
            return 1
        fi
    else
        echo "⚠ NGINX не установлен, проверка пропущена"
        return 2
    fi
}

# Функция проверки конфигурации Bind9
check_bind9_config() {
    if command -v named-checkconf &> /dev/null; then
        if named-checkconf 2>/dev/null; then
            echo "✓ Конфигурация Bind9 корректна"
            return 0
        else
            echo "✗ Ошибка в конфигурации Bind9"
            named-checkconf 2>&1 | grep -i error || true
            return 1
        fi
    else
        echo "⚠ Bind9 не установлен, проверка пропущена"
        return 2
    fi
}

# Финальная информация
echo ""
echo "=== Подготовка завершена ==="
echo ""
echo "Сервер Ansible готов к работе!"
echo ""
echo "Следующие шаги:"
echo "1. Настройте inventory файл: $ANSIBLE_DIR/inventory/hosts"
echo "2. Настройте переменные в: $ANSIBLE_DIR/group_vars/"
echo "3. Скопируйте SSH ключи на клиентов (рекомендуется):"
echo "   ssh-copy-id root@IP_CLIENT"
echo ""
echo "ВАЖНО: Ansible - безагентная система!"
echo "  - Не требуется установка ПО на клиентах"
echo "  - Взаимодействие происходит через SSH"
echo "  - На клиентах используется стандартный Python"
echo ""
echo "Для подключения используйте:"
echo "  ssh root@$IP_ADDRESS"
echo ""
echo "Проверка подключения к клиентам:"
echo "  cd $ANSIBLE_DIR"
echo "  ansible all -i inventory/hosts -m ping"
echo ""
echo "=== Команды для проверки после развертывания ==="
echo ""
echo "1. Проверка статуса DNS сервера:"
echo "   systemctl status bind9"
echo "   check_service_status bind9"
echo ""
echo "2. Проверка статуса NGINX:"
echo "   systemctl status nginx"
echo "   check_service_status nginx"
echo ""
echo "3. Проверка разрешения FQDN (после настройки DNS):"
echo "   dig @$IP_ADDRESS server.example.local"
echo "   dig @$IP_ADDRESS alias.example.local"
echo "   check_dns_resolution server.example.local $IP_ADDRESS"
echo "   check_dns_resolution alias.example.local $IP_ADDRESS"
echo ""
echo "4. Проверка конфигурации Bind9:"
echo "   named-checkconf"
echo "   named-checkzone example.local /etc/bind/zones/db.example.local"
echo ""
echo "5. Проверка конфигурации NGINX:"
echo "   nginx -t"
echo ""
echo "6. Запуск playbook:"
echo "   cd $ANSIBLE_DIR"
echo "   ansible-playbook playbook.yml --tags \"untagged\"          # Базовая установка DNS"
echo "   ansible-playbook playbook.yml --tags \"untagged,my_dns\"  # Добавление CNAME"
echo "   ansible-playbook playbook.yml --tags \"nginx\"             # Установка NGINX"
echo ""
echo "ВАЖНО: После настройки Ansible рекомендуется:"
echo "  1. Отключить парольную аутентификацию SSH"
echo "  2. Настроить SSH ключи"
echo "  3. Отключить root-вход по SSH (создать обычного пользователя)"
echo ""
