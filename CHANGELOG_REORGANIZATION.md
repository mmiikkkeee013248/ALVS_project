# История реорганизации структуры проекта

## Дата: 2025-01-17

### Изменения

Проект был реорганизован для улучшения структуры и навигации.

#### Перемещенные файлы и директории

1. **Приложение Flask** → `app/`
   - `web_app.py` → `app/web_app.py`
   - `db.py` → `app/db.py`
   - `logger.py` → `app/logger.py`
   - `app.py` → `app/app.py`
   - Добавлен `app/__init__.py`

2. **Docker конфигурация** → `config/docker/`
   - `Dockerfile` → `config/docker/Dockerfile`
   - `docker-compose.yml` → `config/docker/docker-compose.yml`
   - `.dockerignore` → `config/docker/.dockerignore`

3. **Мониторинг** → `config/monitoring/`
   - `prometheus.yml` → `config/monitoring/prometheus.yml`
   - `promtail.yml` → `config/monitoring/promtail.yml`
   - `grafana/` → `config/monitoring/grafana/`

4. **Ansible инфраструктура** → `infrastructure/ansible/`
   - `ansible/` → `infrastructure/ansible/`

5. **Скрипты** → `scripts/`
   - `init_ansi.sh` → `scripts/init_ansi.sh`
   - `init_ansi_client.sh` → `scripts/init_ansi_client.sh`
   - `for_ansi.sh` → `scripts/for_ansi.sh`

6. **Документация** → `docs/`
   - `DOCKER.md` → `docs/DOCKER.md`
   - `MONITORING.md` → `docs/MONITORING.md`
   - `OBSERVABILITY_REPORT.md` → `docs/OBSERVABILITY_REPORT.md`

7. **Логи** → `logs/`
   - `app.log` → `logs/app.log`
   - Создана директория `logs/` с `.gitkeep`

#### Обновленные файлы

1. **Импорты в Python файлах:**
   - `app/web_app.py`: обновлены импорты на `from app import ...`
   - `app/app.py`: обновлены импорты
   - `tests/test_web_app.py`: обновлены импорты
   - `tests/test_db.py`: обновлены импорты

2. **Конфигурационные файлы:**
   - `config/docker/docker-compose.yml`: обновлены пути к файлам
   - `config/docker/Dockerfile`: обновлены пути копирования файлов
   - `scripts/init_ansi.sh`: обновлен путь к ansible директории

3. **Документация:**
   - `README.md`: обновлена структура проекта
   - Создан `STRUCTURE.md`: описание новой структуры
   - Создан `REORGANIZATION_PLAN.md`: план реорганизации

4. **Git:**
   - `.gitignore`: добавлена директория `logs/`

#### Новые файлы

- `app/__init__.py` - инициализация пакета приложения
- `logs/.gitkeep` - для сохранения директории в git
- `STRUCTURE.md` - описание структуры проекта
- `REORGANIZATION_PLAN.md` - план реорганизации
- `CHANGELOG_REORGANIZATION.md` - этот файл

### Преимущества новой структуры

1. **Разделение ответственности**: код, конфигурация, инфраструктура разделены
2. **Лучшая навигация**: легче найти нужные файлы
3. **Масштабируемость**: проще добавлять новые компоненты
4. **Стандартная структура**: соответствует best practices Python проектов

### Миграция для разработчиков

Если вы работали со старой структурой:

1. Обновите импорты в вашем коде:
   ```python
   # Старый способ
   from db import get_connection
   from logger import app_logger
   
   # Новый способ
   from app.db import get_connection
   from app.logger import app_logger
   ```

2. Обновите пути запуска:
   ```bash
   # Старый способ
   python web_app.py
   
   # Новый способ
   python -m app.web_app
   ```

3. Обновите пути Docker:
   ```bash
   # Старый способ
   docker-compose up -d
   
   # Новый способ
   cd config/docker
   docker-compose up -d
   ```

### Обратная совместимость

Для обратной совместимости можно создать символические ссылки или обертки, но рекомендуется обновить код на новую структуру.
