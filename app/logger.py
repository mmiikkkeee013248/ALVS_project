"""
Модуль для настройки логирования приложения.
"""
import logging
import os
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

    # Обработчик для файла с ротацией (макс. 5 МБ, 3 резервные копии)
    file_handler = RotatingFileHandler(
        log_file, maxBytes=5 * 1024 * 1024, backupCount=3, encoding="utf-8"
    )
    file_handler.setLevel(level)
    file_handler.setFormatter(formatter)
    logger.addHandler(file_handler)

    # Обработчик для консоли
    console_handler = logging.StreamHandler()
    console_handler.setLevel(level)
    console_handler.setFormatter(formatter)
    logger.addHandler(console_handler)

    return logger


# Создаём глобальный логгер для приложения
app_logger = setup_logger()
