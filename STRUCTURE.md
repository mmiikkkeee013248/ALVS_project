# Структура проекта ALVS_project

## Обзор

Проект организован по принципу разделения ответственности для лучшей навигации и масштабируемости.

```
ALVS_project/
├── app/                          # Основное приложение Flask
│   ├── __init__.py
│   ├── web_app.py                # Веб-приложение Flask
│   ├── db.py                     # Модуль работы с БД
│   ├── logger.py                 # Модуль логирования
│   └── app.py                    # GUI приложение (tkinter)
│
├── templates/                     # HTML шаблоны
│   └── index.html
│
├── tests/                         # Тесты
│   ├── conftest.py
│   ├── test_web_app.py
│   └── test_db.py
│
├── config/                        # Конфигурационные файлы
│   ├── docker/
│   │   ├── Dockerfile
│   │   ├── docker-compose.yml
│   │   └── .dockerignore
│   └── monitoring/
│       ├── prometheus.yml
│       ├── promtail.yml
│       └── grafana/
│           ├── dashboards/
│           └── provisioning/
│
├── infrastructure/                # Инфраструктура как код
│   └── ansible/
│       ├── ansible.cfg
│       ├── playbook.yml
│       ├── inventory/
│       ├── group_vars/
│       └── roles/
│           ├── dns/
│           └── nginx/
│
├── scripts/                       # Скрипты
│   ├── init_ansi.sh              # Подготовка сервера Ansible
│   └── for_ansi.sh               # Старый скрипт подготовки
│
├── docs/                          # Документация
│   ├── DOCKER.md
│   ├── MONITORING.md
│   └── OBSERVABILITY_REPORT.md
│
├── logs/                          # Логи приложения
│   └── app.log
│
├── requirements.txt               # Зависимости Python
├── .gitignore                     # Git ignore правила
├── README.md                       # Главная документация
└── STRUCTURE.md                    # Этот файл
```

## Описание директорий

### `app/`
Основной код приложения Flask. Содержит:
- `web_app.py` - веб-приложение с маршрутами
- `db.py` - модуль работы с PostgreSQL
- `logger.py` - настройка логирования
- `app.py` - GUI приложение (опционально)

### `templates/`
HTML шаблоны для веб-интерфейса.

### `tests/`
Тесты приложения (pytest).

### `config/`
Конфигурационные файлы, разделенные по категориям:
- `docker/` - Docker конфигурация
- `monitoring/` - конфигурация мониторинга (Prometheus, Grafana, Loki)

### `infrastructure/`
Инфраструктура как код:
- `ansible/` - Ansible playbooks и роли для развертывания DNS и NGINX

### `scripts/`
Вспомогательные скрипты для подготовки окружения:
- `init_ansi.sh` - подготовка сервера Ansible (установка Ansible и необходимых пакетов)
- `for_ansi.sh` - старый скрипт подготовки (может быть удален)

**Примечание:** Ansible - безагентная система, поэтому клиентский скрипт не требуется. На клиентах достаточно стандартного Python и SSH сервера.

### `docs/`
Документация проекта.

### `logs/`
Логи приложения (добавлено в .gitignore).

## Запуск приложения

### Локально
```bash
cd ALVS_project
python -m app.web_app
```

### Docker
```bash
cd config/docker
docker-compose up -d
```

### Тесты
```bash
cd ALVS_project
pytest tests/
```

## Импорты

После реорганизации все импорты используют префикс `app.`:

```python
from app.db import get_connection
from app.logger import app_logger
from app.web_app import create_app
```

## Миграция

Если вы обновляете проект с старой структуры:
1. Обновите импорты в вашем коде
2. Обновите пути в конфигурационных файлах
3. Проверьте работоспособность тестов
