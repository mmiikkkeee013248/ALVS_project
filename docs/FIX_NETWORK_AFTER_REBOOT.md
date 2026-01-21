# Исправление сетевых настроек после перезагрузки

## Проблема

После перезагрузки VM настройки второго сетевого интерфейса (`enp0s8` - Host-Only) исчезают:
- `enp0s8` находится в состоянии `DOWN`
- Нет IP адреса `192.168.56.x`
- `enp0s3` получает динамический IP из другой сети (`192.168.100.x`)

## Решение

Создан отдельный Ansible playbook для настройки сети, который можно запускать отдельно или он автоматически выполнится при развертывании.

### Вариант 1: Запуск через Ansible (рекомендуется)

```bash
cd ~/ALVS_project/config/kubernetes/ansible
ansible-playbook -i inventory/hosts.yml playbooks/00-configure-network.yml
```

### Вариант 2: Ручная настройка через netplan

Если Ansible недоступен, выполните на каждой VM:

```bash
# На k8s-master
sudo tee /etc/netplan/01-netcfg.yaml <<EOF
network:
  version: 2
  renderer: networkd
  ethernets:
    enp0s3:
      dhcp4: true
      optional: true
    enp0s8:
      dhcp4: false
      addresses:
        - 192.168.56.10/24
      nameservers:
        addresses:
          - 8.8.8.8
          - 8.8.4.4
EOF

# На k8s-worker-1 (замените IP на 192.168.56.11)
# На k8s-worker-2 (замените IP на 192.168.56.12)

# Применить настройки
sudo netplan apply

# Проверить
ip addr show enp0s8
```

### Вариант 3: Альтернатива через systemd-networkd (если netplan не работает)

```bash
# Создать конфигурацию
sudo tee /etc/systemd/network/20-enp0s8.network <<EOF
[Match]
Name=enp0s8

[Network]
Address=192.168.56.10/24
DNS=8.8.8.8
DNS=8.8.4.4
EOF

# Включить и перезапустить
sudo systemctl enable systemd-networkd
sudo systemctl restart systemd-networkd

# Проверить
ip addr show enp0s8
```

## Почему настройки исчезают?

Возможные причины:
1. **Cloud-init перезаписывает конфигурацию** - удалите или отключите cloud-init конфигурации
2. **Несколько конфигураций netplan конфликтуют** - оставьте только одну (`01-netcfg.yaml`)
3. **Интерфейс не активируется автоматически** - проверьте, что он включен в VirtualBox

## Проверка после настройки

```bash
# Проверить интерфейсы
ip addr show

# Проверить, что enp0s8 имеет правильный IP
ip addr show enp0s8 | grep "192.168.56"

# Проверить доступность
ping -c 2 192.168.56.10  # с других узлов
```

## Автоматическое применение при перезагрузке

Настройки netplan должны применяться автоматически при загрузке. Если этого не происходит:

1. Проверьте, что файл `/etc/netplan/01-netcfg.yaml` существует
2. Проверьте синтаксис: `sudo netplan try`
3. Убедитесь, что нет других конфигураций netplan, которые могут конфликтовать:
   ```bash
   ls -la /etc/netplan/
   ```

## Интеграция в основной playbook

Настройка сети теперь автоматически выполняется в начале развертывания через `site.yml`:
- Сначала выполняется `00-configure-network.yml`
- Затем `01-prepare-nodes.yml` и остальные

Если нужно настроить сеть отдельно:
```bash
ansible-playbook -i inventory/hosts.yml playbooks/00-configure-network.yml
```
