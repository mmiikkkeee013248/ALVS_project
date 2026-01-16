import re
import time
from flask import Flask, render_template, request, redirect, url_for, flash
from prometheus_client import Counter, Histogram, Gauge, generate_latest, CONTENT_TYPE_LATEST

from app import db
from app.logger import app_logger

# Метрики Prometheus
http_requests_total = Counter(
    'http_requests_total',
    'Total number of HTTP requests',
    ['method', 'path', 'status']
)

http_request_duration_seconds = Histogram(
    'http_request_duration_seconds',
    'Duration of HTTP requests in seconds',
    ['method', 'path'],
    buckets=[0.01, 0.05, 0.1, 0.5, 1, 2, 5]
)

http_response_time_seconds = Gauge(
    'http_response_time_seconds',
    'Last HTTP response time in seconds',
    ['method', 'path']
)


def create_app():
    """
    Фабрика приложения Flask, чтобы было удобно тестировать.
    """
    import os
    # Определяем путь к директории templates относительно корня проекта
    template_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'templates')
    app = Flask(__name__, template_folder=template_dir)
    # Для flash-сообщений нужен секретный ключ (для простоты — константа)
    app.config["SECRET_KEY"] = "dev-secret-key"

    # Инициализация БД при первом запросе
    @app.before_request
    def _init_db():
        if not hasattr(app, "_db_initialized"):
            db.init_db()
            app._db_initialized = True

    # Middleware для сбора метрик Prometheus
    @app.before_request
    def before_request():
        request.start_time = time.time()

    @app.after_request
    def after_request(response):
        # Пропускаем метрики endpoint
        if request.path == '/metrics':
            return response

        duration = time.time() - request.start_time
        method = request.method
        path = request.path
        status = str(response.status_code)

        # Обновляем метрики
        http_requests_total.labels(method=method, path=path, status=status).inc()
        http_request_duration_seconds.labels(method=method, path=path).observe(duration)
        http_response_time_seconds.labels(method=method, path=path).set(duration)

        return response

    # Endpoint для метрик Prometheus
    @app.route('/metrics')
    def metrics():
        return generate_latest(), 200, {'Content-Type': CONTENT_TYPE_LATEST}

    @app.route("/", methods=["GET"])
    def index():
        try:
            contacts = db.get_all_contacts()
            app_logger.info(f"Загружено контактов: {len(contacts)}")
        except Exception as e:
            app_logger.error(f"Ошибка при загрузке контактов: {str(e)}")
            flash(f"Ошибка при загрузке контактов: {str(e)}", "error")
            contacts = []
        return render_template("index.html", contacts=contacts)

    @app.route("/add", methods=["POST"])
    def add():
        name = request.form.get("name", "").strip()
        email = request.form.get("email", "").strip()
        if not name or not email:
            flash("Имя и email обязательны для заполнения", "error")
            return redirect(url_for("index"))

        if not validate_email(email):
            flash("Некорректный формат email адреса", "error")
            return redirect(url_for("index"))

        try:
            db.add_contact(name, email)
            app_logger.info(f"Добавлен контакт: name='{name}', email='{email}'")
            flash("Контакт добавлен", "success")
        except Exception as e:
            app_logger.error(f"Ошибка при добавлении контакта: name='{name}', email='{email}', error={str(e)}")
            flash(f"Ошибка при добавлении контакта: {str(e)}", "error")
        return redirect(url_for("index"))

    @app.route("/edit/<int:contact_id>", methods=["POST"])
    def edit(contact_id: int):
        name = request.form.get("name", "").strip()
        email = request.form.get("email", "").strip()
        if not name or not email:
            flash("Имя и email обязательны для заполнения", "error")
            return redirect(url_for("index"))

        if not validate_email(email):
            flash("Некорректный формат email адреса", "error")
            return redirect(url_for("index"))

        try:
            db.update_contact(contact_id, name, email)
            app_logger.info(f"Обновлён контакт: id={contact_id}, name='{name}', email='{email}'")
            flash("Контакт обновлён", "success")
        except Exception as e:
            app_logger.error(f"Ошибка при обновлении контакта: id={contact_id}, name='{name}', email='{email}', error={str(e)}")
            flash(f"Ошибка при обновлении контакта: {str(e)}", "error")
        return redirect(url_for("index"))

    @app.route("/delete/<int:contact_id>", methods=["POST"])
    def delete(contact_id: int):
        try:
            db.delete_contact(contact_id)
            app_logger.info(f"Удалён контакт: id={contact_id}")
            flash("Контакт удалён", "success")
        except Exception as e:
            app_logger.error(f"Ошибка при удалении контакта: id={contact_id}, error={str(e)}")
            flash(f"Ошибка при удалении контакта: {str(e)}", "error")
        return redirect(url_for("index"))

    return app


def validate_email(email: str) -> bool:
    """
    Простая валидация email адреса.
    """
    pattern = r"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$"
    return re.match(pattern, email) is not None


if __name__ == "__main__":
    app = create_app()
    app.run(debug=True)

