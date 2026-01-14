from flask import Flask, render_template, request, redirect, url_for, flash

import db


def create_app():
    """
    Фабрика приложения Flask, чтобы было удобно тестировать.
    """
    app = Flask(__name__)
    # Для flash-сообщений нужен секретный ключ (для простоты — константа)
    app.config["SECRET_KEY"] = "dev-secret-key"

    # Инициализация БД при первом запросе
    @app.before_request
    def _init_db():
        if not hasattr(app, "_db_initialized"):
            db.init_db()
            app._db_initialized = True

    @app.route("/", methods=["GET"])
    def index():
        contacts = db.get_all_contacts()
        return render_template("index.html", contacts=contacts)

    @app.route("/add", methods=["POST"])
    def add():
        name = request.form.get("name", "").strip()
        email = request.form.get("email", "").strip()
        if not name or not email:
            flash("Имя и email обязательны для заполнения", "error")
            return redirect(url_for("index"))

        db.add_contact(name, email)
        flash("Контакт добавлен", "success")
        return redirect(url_for("index"))

    @app.route("/edit/<int:contact_id>", methods=["POST"])
    def edit(contact_id: int):
        name = request.form.get("name", "").strip()
        email = request.form.get("email", "").strip()
        if not name or not email:
            flash("Имя и email обязательны для заполнения", "error")
            return redirect(url_for("index"))

        db.update_contact(contact_id, name, email)
        flash("Контакт обновлён", "success")
        return redirect(url_for("index"))

    @app.route("/delete/<int:contact_id>", methods=["POST"])
    def delete(contact_id: int):
        db.delete_contact(contact_id)
        flash("Контакт удалён", "success")
        return redirect(url_for("index"))

    return app


if __name__ == "__main__":
    app = create_app()
    app.run(debug=True)

