# План реорганизации структуры проекта

## Текущая структура
```
ALVS_project/
├── [приложение Flask] - файлы в корне
├── ansible/ - инфраструктура
├── grafana/ - конфигурация мониторинга
├── templates/ - HTML шаблоны
├── tests/ - тесты
├── [разные .md файлы] - документация в корне
├── [разные .yml файлы] - конфигурации в корне
└── [скрипты .sh] - скрипты в корне
```

## Предлагаемая структура
```
ALVS_project/
├── app/                          # Основное приложение
│   ├── __init__.py
│   ├── web_app.py
│   ├── db.py
│   ├── logger.py
│   └── app.py (GUI)
│
├── templates/                     # HTML шаблоны (остается)
│   └── index.html
│
├── tests/                         # Тесты (остается)
│   ├── conftest.py
│   ├── test_web_app.py
│   └── test_db.py
│
├── config/                        # Конфигурационные файлы
│   ├── docker/
│   │   ├── Dockerfile
│   │   ├── docker-compose.yml
│   │   └── .dockerignore
│   ├── monitoring/
│   │   ├── prometheus.yml
│   │   ├── promtail.yml
│   │   └── grafana/
│   │       ├── dashboards/
│   │       └── provisioning/
│   └── ci/
│       └── .github/
│           └── workflows/
│
├── infrastructure/                # Инфраструктура как код
│   └── ansible/
│       ├── ansible.cfg
│       ├── playbook.yml
│       ├── inventory/
│       ├── group_vars/
│       └── roles/
│
├── scripts/                       # Скрипты
│   ├── init_ansi.sh
│   ├── init_ansi_client.sh
│   └── for_ansi.sh
│
├── docs/                          # Документация
│   ├── DOCKER.md
│   ├── MONITORING.md
│   └── OBSERVABILITY_REPORT.md
│
├── logs/                          # Логи (создать, добавить в .gitignore)
│   └── .gitkeep
│
├── requirements.txt               # Зависимости (остается в корне)
├── .gitignore                     # Git ignore (остается в корне)
├── README.md                       # Главный README (остается в корне)
└── Untitled                       # Временные файлы (можно удалить)
```

## Преимущества новой структуры

1. **Разделение ответственности**: приложение, инфраструктура, конфигурация разделены
2. **Лучшая навигация**: легче найти нужные файлы
3. **Масштабируемость**: проще добавлять новые компоненты
4. **Стандартная структура**: соответствует best practices

## Шаги миграции

1. Создать новые директории
2. Переместить файлы
3. Обновить пути в конфигурационных файлах
4. Обновить документацию
5. Проверить работоспособность
