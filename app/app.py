import tkinter as tk
from tkinter import messagebox, simpledialog

from app.db import init_db, get_all_contacts, add_contact, update_contact, delete_contact


class ContactsApp(tk.Tk):
    def __init__(self):
        super().__init__()

        self.title("Простое приложение с PostgreSQL")
        self.geometry("500x400")

        # Виджеты
        self.listbox = tk.Listbox(self, height=15)
        self.listbox.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)

        btn_frame = tk.Frame(self)
        btn_frame.pack(fill=tk.X, padx=10, pady=(0, 10))

        self.btn_add = tk.Button(btn_frame, text="Добавить", command=self.add_contact_ui)
        self.btn_add.pack(side=tk.LEFT, padx=5)

        self.btn_edit = tk.Button(btn_frame, text="Изменить", command=self.edit_contact_ui)
        self.btn_edit.pack(side=tk.LEFT, padx=5)

        self.btn_delete = tk.Button(btn_frame, text="Удалить", command=self.delete_contact_ui)
        self.btn_delete.pack(side=tk.LEFT, padx=5)

        self.status_var = tk.StringVar()
        self.status_bar = tk.Label(self, textvariable=self.status_var, anchor="w")
        self.status_bar.pack(fill=tk.X, padx=10, pady=(0, 5))

        self.contacts_cache = []

        self.refresh_contacts()

    def set_status(self, text: str):
        self.status_var.set(text)

    def refresh_contacts(self):
        try:
            self.contacts_cache = get_all_contacts()
        except Exception as e:
            messagebox.showerror("Ошибка", f"Не удалось загрузить контакты:\n{e}")
            self.set_status("Ошибка загрузки контактов")
            return

        self.listbox.delete(0, tk.END)
        for c in self.contacts_cache:
            self.listbox.insert(tk.END, f"{c['id']}: {c['name']} <{c['email']}>")

        self.set_status(f"Загружено записей: {len(self.contacts_cache)}")

    def _ask_contact_data(self, title: str, name_default: str = "", email_default: str = ""):
        name = simpledialog.askstring(title, "Имя:", initialvalue=name_default, parent=self)
        if name is None:
            return None, None
        email = simpledialog.askstring(title, "Email:", initialvalue=email_default, parent=self)
        if email is None:
            return None, None
        return name.strip(), email.strip()

    def add_contact_ui(self):
        name, email = self._ask_contact_data("Добавить контакт")
        if not name or not email:
            return

        try:
            add_contact(name, email)
            self.set_status("Контакт добавлен")
            self.refresh_contacts()
        except Exception as e:
            messagebox.showerror("Ошибка", f"Не удалось добавить контакт:\n{e}")

    def _get_selected_contact(self):
        try:
            index = self.listbox.curselection()[0]
        except IndexError:
            messagebox.showwarning("Нет выбора", "Сначала выберите контакт в списке.")
            return None
        return self.contacts_cache[index]

    def edit_contact_ui(self):
        contact = self._get_selected_contact()
        if not contact:
            return

        name, email = self._ask_contact_data(
            "Изменить контакт", name_default=contact["name"], email_default=contact["email"]
        )
        if not name or not email:
            return

        try:
            update_contact(contact["id"], name, email)
            self.set_status("Контакт обновлён")
            self.refresh_contacts()
        except Exception as e:
            messagebox.showerror("Ошибка", f"Не удалось обновить контакт:\n{e}")

    def delete_contact_ui(self):
        contact = self._get_selected_contact()
        if not contact:
            return

        if not messagebox.askyesno("Подтверждение", f"Удалить контакт {contact['name']}?"):
            return

        try:
            delete_contact(contact["id"])
            self.set_status("Контакт удалён")
            self.refresh_contacts()
        except Exception as e:
            messagebox.showerror("Ошибка", f"Не удалось удалить контакт:\n{e}")


def main():
    # Инициализация БД (создание таблицы, если её нет)
    try:
        init_db()
    except Exception as e:
        tk.messagebox.showerror(
            "Ошибка подключения",
            f"Не удалось инициализировать базу данных.\n"
            f"Проверьте настройки подключения в переменных окружения.\n\n{e}",
        )
        return

    app = ContactsApp()
    app.mainloop()


if __name__ == "__main__":
    main()

