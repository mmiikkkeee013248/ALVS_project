import os
from contextlib import contextmanager

import psycopg2
from psycopg2.extras import RealDictCursor
from dotenv import load_dotenv


load_dotenv()


def get_db_config():
    """
    Читает настройки подключения к БД из переменных окружения.

    Требуются:
    - PG_HOST
    - PG_PORT
    - PG_DB
    - PG_USER
    - PG_PASSWORD
    """
    return {
        "host": os.getenv("PG_HOST", "localhost"),
        "port": os.getenv("PG_PORT", "5432"),
        "dbname": os.getenv("PG_DB", "test_db"),
        "user": os.getenv("PG_USER", "postgres"),
        "password": os.getenv("PG_PASSWORD", "postgres"),
    }


@contextmanager
def get_connection():
    cfg = get_db_config()
    conn = psycopg2.connect(
        host=cfg["host"],
        port=cfg["port"],
        dbname=cfg["dbname"],
        user=cfg["user"],
        password=cfg["password"],
        cursor_factory=RealDictCursor,
    )
    try:
        yield conn
    finally:
        conn.close()


def init_db():
    """
    Создает простую таблицу contacts, если её ещё нет.
    """
    create_table_sql = """
    CREATE TABLE IF NOT EXISTS contacts (
        id SERIAL PRIMARY KEY,
        name TEXT NOT NULL,
        email TEXT NOT NULL
    );
    """
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(create_table_sql)
        conn.commit()


def get_all_contacts():
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT id, name, email FROM contacts ORDER BY id;")
            rows = cur.fetchall()
    return rows


def add_contact(name: str, email: str):
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "INSERT INTO contacts (name, email) VALUES (%s, %s);",
                (name, email),
            )
        conn.commit()


def update_contact(contact_id: int, name: str, email: str):
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "UPDATE contacts SET name = %s, email = %s WHERE id = %s;",
                (name, email, contact_id),
            )
        conn.commit()


def delete_contact(contact_id: int):
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("DELETE FROM contacts WHERE id = %s;", (contact_id,))
        conn.commit()

