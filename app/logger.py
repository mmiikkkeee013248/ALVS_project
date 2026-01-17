"""
Модуль для настройки логирования приложения.
"""
import logging
import os
import sys
from logging.handlers import RotatingFileHandler


def setup_logger(name: str = "web_app", log_file: str = "app.log", level: int = logging.INFO):
    """
    Настраивает и возвращает логгер с ротацией файлов.

    Args:
        name: Имя логгера
        log_file: Путь к файлу логов
        level: Уровень логирования

    Returns:
        Настроенный логгер
    """
    logger = logging.getLogger(name)
    logger.setLevel(level)

    # Если обработчики уже добавлены, не добавляем повторно
    if logger.handlers:
        return logger

    # Формат логов: время, уровень, сообщение
    formatter = logging.Formatter(
        "%(asctime)s - %(name)s - %(levelname)s - %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )

    # Проверка и создание файла логов, если нужно
    log_dir = os.path.dirname(log_file) if os.path.dirname(log_file) else "."
    if log_dir and not os.path.exists(log_dir):
        try:
            os.makedirs(log_dir, exist_ok=True)
        except (OSError, PermissionError):
            # Если не удалось создать директорию (например, в CI/CD), используем текущую директорию
            log_file = os.path.basename(log_file)
            log_dir = "."
    
    # Если путь является директорией (ошибка volume mapping), используем файл внутри
    if os.path.exists(log_file) and os.path.isdir(log_file):
        log_file = os.path.join(log_file, "app.log")
    
    # Создаем файл, если его нет
    if not os.path.exists(log_file):
        try:
            with open(log_file, 'a'):
                pass
        except (OSError, IOError):
            # Если не удалось создать файл, используем только консольный вывод
            log_file = None

    # Обработчик для файла с ротацией (макс. 5 МБ, 3 резервные копии)
    if log_file:
        try:
            file_handler = RotatingFileHandler(
                log_file, maxBytes=5 * 1024 * 1024, backupCount=3, encoding="utf-8"
            )
            file_handler.setLevel(level)
            file_handler.setFormatter(formatter)
            logger.addHandler(file_handler)
        except (OSError, IOError) as e:
            # Если не удалось создать file handler, логируем ошибку и продолжаем с консолью
            print(f"Warning: Could not create file handler for {log_file}: {e}", file=sys.stderr)

    # Обработчик для консоли
    console_handler = logging.StreamHandler()
    console_handler.setLevel(level)
    console_handler.setFormatter(formatter)
    logger.addHandler(console_handler)

    return logger


# Создаём глобальный логгер для приложения
# Используем путь к логам в директории logs, если доступна, иначе текущую директорию
_log_dir = os.getenv("LOG_DIR")
if _log_dir and os.path.exists(_log_dir) and os.access(_log_dir, os.W_OK):
    _log_file = os.path.join(_log_dir, "app.log")
else:
    # Пробуем использовать директорию logs в текущей директории
    _logs_dir = os.path.join(os.getcwd(), "logs")
    try:
        # Проверяем, можем ли мы создать/использовать директорию logs
        if not os.path.exists(_logs_dir):
            os.makedirs(_logs_dir, exist_ok=True)
        if os.path.exists(_logs_dir) and os.access(_logs_dir, os.W_OK):
            _log_file = os.path.join(_logs_dir, "app.log")
        else:
            _log_file = "app.log"
    except (OSError, PermissionError):
        # Если не удалось создать директорию, используем просто app.log в текущей директории
        _log_file = "app.log"

app_logger = setup_logger(log_file=_log_file)
