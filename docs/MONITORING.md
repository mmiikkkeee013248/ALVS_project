# Настройка мониторинга (Observability)

Инструкции по настройке системы мониторинга для проекта согласно лабораторной работе №10.

## Компоненты

- **Prometheus** - сбор метрик
- **Grafana** - визуализация метрик и логов
- **Loki** - сбор и хранение логов
- **Promtail** - агент для отправки логов в Loki

## 1. Установка Prometheus

### На Ubuntu/Debian:

```bash
# Скачать последнюю версию
VERSION=$(curl -s https://api.github.com/repos/prometheus/prometheus/releases/latest | grep tag_name | cut -d '"' -f 4)
wget https://github.com/prometheus/prometheus/releases/download/${VERSION}/prometheus-${VERSION}.linux-amd64.tar.gz

# Распаковать
tar xvfz prometheus-*.tar.gz
cd prometheus-*

# Переместить бинарники
sudo mv prometheus promtool /usr/local/bin/
sudo mkdir -p /etc/prometheus
sudo mv prometheus.yml /etc/prometheus/
```

### Создать конфигурацию `/etc/prometheus/prometheus.yml`:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'flask-app'
    static_configs:
      - targets: ['localhost:5000']
    metrics_path: '/metrics'
```

### Создать systemd сервис `/etc/systemd/system/prometheus.service`:

```ini
[Unit]
Description=Prometheus
After=network.target

[Service]
Type=simple
User=prometheus
ExecStart=/usr/local/bin/prometheus \
  --config.file=/etc/prometheus/prometheus.yml \
  --storage.tsdb.path=/var/lib/prometheus/ \
  --web.console.templates=/etc/prometheus/consoles \
  --web.console.libraries=/etc/prometheus/console_libraries \
  --web.listen-address=0.0.0.0:9090

[Install]
WantedBy=multi-user.target
```

### Запустить Prometheus:

```bash
sudo useradd --no-create-home --shell /bin/false prometheus
sudo mkdir -p /var/lib/prometheus
sudo chown prometheus:prometheus /var/lib/prometheus
sudo systemctl daemon-reload
sudo systemctl enable prometheus
sudo systemctl start prometheus
```

Prometheus будет доступен по адресу: `http://localhost:9090`

## 2. Установка Grafana

```bash
# Добавить репозиторий
sudo apt-get install -y software-properties-common
sudo add-apt-repository "deb https://packages.grafana.com/oss/deb stable main"
wget -q -O - https://packages.grafana.com/gpg.key | sudo apt-key add -

# Установить
sudo apt-get update
sudo apt-get install grafana

# Запустить
sudo systemctl daemon-reload
sudo systemctl enable grafana-server
sudo systemctl start grafana-server
```

Grafana будет доступна по адресу: `http://localhost:3000` (логин: admin, пароль: admin)

### Настройка Prometheus как источника данных в Grafana:

1. Зайти в Grafana (http://localhost:3000)
2. Configuration → Data Sources → Add data source
3. Выбрать Prometheus
4. URL: `http://localhost:9090`
5. Save & Test

## 3. Установка Loki

```bash
# Скачать последнюю версию
VERSION=$(curl -s https://api.github.com/repos/grafana/loki/releases/latest | grep tag_name | cut -d '"' -f 4)
curl -LO "https://github.com/grafana/loki/releases/download/${VERSION}/loki-linux-amd64.zip"
unzip loki-linux-amd64.zip
chmod +x loki-linux-amd64
sudo mv loki-linux-amd64 /usr/local/bin/loki
```

### Создать конфигурацию `/etc/loki-local-config.yaml`:

```yaml
auth_enabled: false

server:
  http_listen_port: 3100
  grpc_listen_port: 9096

common:
  path_prefix: /tmp/loki
  storage:
    filesystem:
      chunks_directory: /tmp/loki/chunks
      rules_directory: /tmp/loki/rules
  replication_factor: 1
  ring:
    instance_addr: 127.0.0.1
    kvstore:
      store: inmemory

schema_config:
  configs:
    - from: 2020-10-24
      store: boltdb-shipper
      object_store: filesystem
      schema: v11
      index:
        prefix: index_
        period: 24h

ruler:
  alertmanager_url: http://localhost:9093
```

### Создать systemd сервис `/etc/systemd/system/loki.service`:

```ini
[Unit]
Description=Loki service
After=network.target

[Service]
Type=simple
User=root
ExecStart=/usr/local/bin/loki -config.file=/etc/loki-local-config.yaml
Restart=always

[Install]
WantedBy=multi-user.target
```

### Запустить Loki:

```bash
sudo systemctl daemon-reload
sudo systemctl enable loki
sudo systemctl start loki
```

## 4. Установка Promtail

```bash
# Скачать последнюю версию
VERSION=$(curl -s https://api.github.com/repos/grafana/loki/releases/latest | grep tag_name | cut -d '"' -f 4)
curl -LO "https://github.com/grafana/loki/releases/download/${VERSION}/promtail-linux-amd64.zip"
unzip promtail-linux-amd64.zip
chmod +x promtail-linux-amd64
sudo mv promtail-linux-amd64 /usr/local/bin/promtail
```

### Создать конфигурацию `/etc/promtail-local-config.yaml`:

```yaml
server:
  http_listen_port: 9080
  grpc_listen_port: 0

positions:
  filename: /var/lib/promtail/positions.yaml

clients:
  - url: http://localhost:3100/loki/api/v1/push

scrape_configs:
  - job_name: flask-app
    static_configs:
      - targets: [localhost]
        labels:
          job: flask-app
          __path__: /path/to/ALVS_project/app.log
```

**Важно:** Замените `/path/to/ALVS_project/app.log` на реальный путь к файлу логов вашего приложения.

### Создать systemd сервис `/etc/systemd/system/promtail.service`:

```ini
[Unit]
Description=Promtail service
After=network.target

[Service]
Type=simple
User=root
ExecStart=/usr/local/bin/promtail -config.file=/etc/promtail-local-config.yaml

[Install]
WantedBy=multi-user.target
```

### Запустить Promtail:

```bash
sudo mkdir -p /var/lib/promtail
sudo systemctl daemon-reload
sudo systemctl enable promtail
sudo systemctl start promtail
```

## 5. Настройка Grafana для работы с Loki

1. Зайти в Grafana (http://localhost:3000)
2. Configuration → Data Sources → Add data source
3. Выбрать Loki
4. URL: `http://localhost:3100`
5. Save & Test

## 6. Создание дашбордов в Grafana

### Дашборд для метрик Prometheus:

1. Create → Dashboard → Add visualization
2. Data source: Prometheus
3. Метрики для отображения:
   - `http_requests_total` - общее количество запросов
   - `http_request_duration_seconds` - время отклика
   - `rate(http_requests_total[5m])` - скорость запросов

### Дашборд для логов Loki:

1. Create → Dashboard → Add visualization
2. Visualization type: Logs
3. Data source: Loki
4. Query: `{job="flask-app"}`

## 7. Проверка работы

1. Запустить Flask приложение:
   ```bash
   python web_app.py
   ```

2. Выполнить несколько запросов к приложению

3. Проверить метрики в Prometheus: http://localhost:9090/graph
   - Запрос: `http_requests_total`

4. Проверить метрики в Grafana: http://localhost:3000

5. Проверить логи в Grafana:
   - Создать панель Logs
   - Query: `{job="flask-app"}`

## Доступные метрики

После настройки приложение экспортирует следующие метрики на `/metrics`:

- `http_requests_total` - счётчик всех HTTP запросов (метки: method, path, status)
- `http_request_duration_seconds` - гистограмма времени выполнения запросов (метки: method, path)
- `http_response_time_seconds` - последнее время отклика (метки: method, path)

## Примеры PromQL запросов

```promql
# Общее количество запросов
http_requests_total

# Количество запросов по методам
sum by (method) (http_requests_total)

# Количество ошибок (статус >= 400)
sum(http_requests_total{status=~"4..|5.."})

# Среднее время отклика
rate(http_request_duration_seconds_sum[5m]) / rate(http_request_duration_seconds_count[5m])

# 95-й перцентиль времени отклика
histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m]))
```

## Логирование

Приложение логирует все операции в файл `app.log`:
- Загрузка контактов
- Добавление контактов
- Обновление контактов
- Удаление контактов
- Ошибки

Promtail автоматически собирает эти логи и отправляет в Loki для анализа в Grafana.
