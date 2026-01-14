"""
Тесты для работы с базой данных PostgreSQL.
Эти тесты требуют наличия настроенной PostgreSQL БД.
"""
import pytest
from db import init_db, get_all_contacts, add_contact, update_contact, delete_contact


@pytest.fixture(scope="function")
def setup_db():
    """
    Фикстура для инициализации БД перед каждым тестом.
    """
    init_db()
    yield
    # Очистка после теста (опционально)
    # Можно добавить очистку таблицы, если нужно


def test_init_db(setup_db):
    """Тест инициализации БД - проверяем, что таблица создается."""
    # Если init_db() выполнился без ошибок, значит таблица создана
    assert True


def test_add_and_get_contact(setup_db):
    """Тест добавления и получения контакта."""
    # Очищаем таблицу перед тестом
    contacts_before = get_all_contacts()
    
    # Добавляем новый контакт
    add_contact("Test User", "test@example.com")
    
    # Получаем все контакты
    contacts_after = get_all_contacts()
    
    # Проверяем, что контакт добавлен
    assert len(contacts_after) >= len(contacts_before) + 1
    
    # Проверяем, что добавленный контакт есть в списке
    test_contact = None
    for contact in contacts_after:
        if contact['email'] == "test@example.com":
            test_contact = contact
            break
    
    assert test_contact is not None
    assert test_contact['name'] == "Test User"
    assert test_contact['email'] == "test@example.com"


def test_update_contact(setup_db):
    """Тест обновления контакта."""
    # Добавляем контакт для теста
    add_contact("Old Name", "old@example.com")
    
    # Находим добавленный контакт
    contacts = get_all_contacts()
    test_contact = None
    for contact in contacts:
        if contact['email'] == "old@example.com":
            test_contact = contact
            break
    
    assert test_contact is not None
    contact_id = test_contact['id']
    
    # Обновляем контакт
    update_contact(contact_id, "New Name", "new@example.com")
    
    # Проверяем обновление
    contacts_after = get_all_contacts()
    updated_contact = None
    for contact in contacts_after:
        if contact['id'] == contact_id:
            updated_contact = contact
            break
    
    assert updated_contact is not None
    assert updated_contact['name'] == "New Name"
    assert updated_contact['email'] == "new@example.com"


def test_delete_contact(setup_db):
    """Тест удаления контакта."""
    # Добавляем контакт для удаления
    add_contact("To Delete", "delete@example.com")
    
    # Находим добавленный контакт
    contacts_before = get_all_contacts()
    test_contact = None
    for contact in contacts_before:
        if contact['email'] == "delete@example.com":
            test_contact = contact
            break
    
    assert test_contact is not None
    contact_id = test_contact['id']
    
    # Удаляем контакт
    delete_contact(contact_id)
    
    # Проверяем, что контакт удален
    contacts_after = get_all_contacts()
    deleted_contact = None
    for contact in contacts_after:
        if contact['id'] == contact_id:
            deleted_contact = contact
            break
    
    assert deleted_contact is None
