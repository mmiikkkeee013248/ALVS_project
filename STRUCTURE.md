# Структура проекта ALVS_project

## Обзор

Проект организован по принципу разделения ответственности для лучшей навигации и масштабируемости.

```
ALVS_project/
├── app/                          # Flask Application
├── templates/                     # HTML Templates
├── tests/                         # Tests
├── config/                        # Configuration
│   ├── docker/                    # Docker setup
│   ├── kubernetes/                # Kubernetes setup
│   │   ├── ansible/               # Ansible for K8s deployment
│   │   ├── manifests/             # K8s manifests
│   │   └── scripts/               # Helper scripts for K8s
│   └── monitoring/                # Monitoring (Prometheus, Grafana)
├── ansible/                       # Old Lab tasks (DNS, Nginx)
├── docs/                          # Documentation
├── scripts/                       # Helper scripts
├── requirements.txt
├── README.md
└── STRUCTURE.md
```

## Описание директорий

### `app/`
Основной код приложения Flask. Содержит:
- `web_app.py` - веб-приложение с маршрутами
- `db.py` - модуль работы с PostgreSQL
- `logger.py` - настройка логирования
- `app.py` - GUI приложение

### `templates/`
HTML шаблоны для веб-интерфейса.

### `tests/`
Тесты приложения (pytest).

### `config/`
Конфигурационные файлы:
- `docker/` - Docker и Docker Compose конфигурация
- `kubernetes/` - Полный набор для K8s: Ansible, манифесты, скрипты
- `monitoring/` - конфигурация Prometheus и Grafana

### `ansible/`
Старые лабораторные работы (DNS, Nginx) для справки.

### `scripts/`
Вспомогательные скрипты для подготовки окружения.

### `docs/`
Документация проекта.

## Запуск приложения

### Локально
```bash
python -m app.web_app
```

### Docker
```bash
cd config/docker
docker-compose up -d
```

### Kubernetes
```bash
cd config/kubernetes
./deploy.sh
```

### Тесты
```bash
pytest tests/
```

## Импорты

Все импорты используют префикс `app.`:

```python
from app.db import get_connection
from app.logger import app_logger
from app.web_app import create_app
```
