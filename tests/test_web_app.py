import types

import pytest

from app.web_app import create_app


class DummyDB:
    """
    Заглушка для функций работы с БД, чтобы тестировать веб-слой
    без реальной PostgreSQL.
    """

    def __init__(self):
        self.contacts = []
        self._next_id = 1
        self._initialized = False

    def init_db(self):
        # В реальном коде создаётся таблица, здесь — только при первом вызове
        if not self._initialized:
            self.contacts.clear()
            self._next_id = 1
            self._initialized = True

    def get_all_contacts(self):
        return list(self.contacts)

    def add_contact(self, name, email):
        self.contacts.append(
            types.SimpleNamespace(id=self._next_id, name=name, email=email)
        )
        self._next_id += 1

    def update_contact(self, contact_id, name, email):
        for c in self.contacts:
            if c.id == contact_id:
                c.name = name
                c.email = email
                break

    def delete_contact(self, contact_id):
        self.contacts = [c for c in self.contacts if c.id != contact_id]


@pytest.fixture()
def dummy_db(monkeypatch):
    """
    Фикстура, подменяющая функции модуля db на тестовую реализацию.
    """
    from importlib import import_module

    db_module = import_module("app.db")
    db = DummyDB()

    monkeypatch.setattr(db_module, "init_db", db.init_db)
    monkeypatch.setattr(db_module, "get_all_contacts", db.get_all_contacts)
    monkeypatch.setattr(db_module, "add_contact", db.add_contact)
    monkeypatch.setattr(db_module, "update_contact", db.update_contact)
    monkeypatch.setattr(db_module, "delete_contact", db.delete_contact)

    return db


@pytest.fixture()
def client(dummy_db):
    """
    Тестовый клиент Flask с подменённой БД.
    """
    app = create_app()
    app.config.update({"TESTING": True})
    with app.test_client() as client:
        yield client


def test_index_page_renders(client):
    resp = client.get("/")
    assert resp.status_code == 200
    assert "Контакты" in resp.get_data(as_text=True)


def test_add_contact(client, dummy_db):
    assert dummy_db.contacts == []

    resp = client.post(
        "/add", data={"name": "Test User", "email": "test@example.com"}, follow_redirects=True
    )
    assert resp.status_code == 200
    assert len(dummy_db.contacts) == 1
    assert dummy_db.contacts[0].name == "Test User"


def test_edit_contact(client, dummy_db):
    # Сначала инициализируем БД (это вызовет init_db)
    client.get("/")
    # Теперь добавляем контакт через заглушку
    dummy_db.add_contact("Old Name", "old@example.com")
    contact_id = dummy_db.contacts[0].id
    assert len(dummy_db.contacts) == 1

    resp = client.post(
        f"/edit/{contact_id}",
        data={"name": "New Name", "email": "new@example.com"},
        follow_redirects=True,
    )
    assert resp.status_code == 200
    # Проверяем, что контакт остался в списке после редактирования
    assert len(dummy_db.contacts) == 1
    assert dummy_db.contacts[0].name == "New Name"
    assert dummy_db.contacts[0].email == "new@example.com"


def test_delete_contact(client, dummy_db):
    dummy_db.add_contact("User", "user@example.com")
    contact_id = dummy_db.contacts[0].id

    resp = client.post(f"/delete/{contact_id}", follow_redirects=True)
    assert resp.status_code == 200
    assert dummy_db.contacts == []

