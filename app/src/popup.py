import customtkinter as ctk

class Popup:
    @staticmethod
    def _show(parent, title, message, color):
        win = ctk.CTkToplevel(parent)
        win.title(title)
        win.geometry("300x160")
        win.resizable(False, False)

        parent.update_idletasks()
        x = parent.winfo_x() + 50
        y = parent.winfo_y() + 50
        win.geometry(f"+{x}+{y}")

        win.transient(parent)
        win.grab_set()
        win.focus_force()

        ctk.CTkLabel(win, text=message, text_color=color, wraplength=250).pack(pady=20)
        ctk.CTkButton(win, text="OK", command=win.destroy).pack(pady=10)

    @staticmethod
    def error(parent, message):
        Popup._show(parent, "Error", message, "red")

    @staticmethod
    def success(parent, message):
        Popup._show(parent, "Success", message, "green")
