#!/bin/bash
# Скрипт для подготовки Astra Linux к работе с Ansible
# Использование: sudo ./for_ansi.sh

set -e

echo "=== Подготовка Astra Linux для работы с Ansible ==="

# Проверка прав root
if [ "$EUID" -ne 0 ]; then 
    echo "Ошибка: Скрипт должен быть запущен с правами root (sudo)"
    exit 1
fi

# Определение версии Astra Linux
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
    dnsutils

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

# Создание директории для Ansible (если нужно)
echo ""
echo "=== Подготовка директорий ==="
mkdir -p /opt/ansible
mkdir -p /var/www
chmod 755 /var/www

# Получение IP адреса
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

# Финальная информация
echo ""
echo "=== Подготовка завершена ==="
echo ""
echo "Система готова к работе с Ansible!"
echo ""
echo "Для подключения с управляющей машины используйте:"
echo "  ssh root@$IP_ADDRESS"
echo ""
echo "Или добавьте в /etc/hosts на управляющей машине:"
echo "  $IP_ADDRESS $FQDN $HOSTNAME"
echo ""
echo "Проверка подключения:"
echo "  ansible all -i inventory/hosts -m ping"
echo ""
echo "ВАЖНО: После настройки Ansible рекомендуется:"
echo "  1. Отключить парольную аутентификацию SSH"
echo "  2. Настроить SSH ключи"
echo "  3. Отключить root-вход по SSH (создать обычного пользователя)"
echo ""
