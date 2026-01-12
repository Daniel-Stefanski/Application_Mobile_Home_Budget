# 👤 Autor

Imię i Nazwisko: [Daniel Stefański]
Projekt inżynierski: Aplikacja mobilna „HomeBudget” — system zarządzania domowym budżetem
Rok: 2025

# 💰 HomeBudget

**HomeBudget** to mobilna aplikacja na system Android, stworzona w języku **Kotlin**, umożliwiająca użytkownikom zarządzanie swoimi finansami domowymi.  
Użytkownik może rejestrować wydatki, przeglądać historię, wyszukiwać oraz sortować dane, a także analizować swoje nawyki finansowe w prosty i przejrzysty sposób.

---

## 🧠 Opis projektu

Aplikacja została zaprojektowana z myślą o prostocie i intuicyjności.  
Pozwala na:

- tworzenie konta użytkownika (rejestracja),
- logowanie za pomocą e-maila i hasła,
- dodawanie nowych wydatków z opisem, kategorią, kwotą i notatką,
- przeglądanie historii wydatków z możliwością wyszukiwania, sortowania i filtrowania,
- wylogowanie się z konta i powrót do ekranu logowania.

Projekt jest częścią pracy inżynierskiej, realizowanej w celu nauki programowania aplikacji mobilnych oraz zarządzania danymi z wykorzystaniem **Room Database**.

---

## ⚙️ Technologie i narzędzia

- **Język:** Kotlin
- **Środowisko:** Android Studio
- **Baza danych:** Room (ORM na SQLite)
- **UI:** XML Layouts + RecyclerView + SearchView + Spinner
- **Asynchroniczność:** Kotlin Coroutines + `lifecycleScope`
- **Architektura:** prosty układ z `Activity` i `DAO`
- **Inne:** SharedPreferences (do przechowywania ID użytkownika), Intenty, Adaptery

---

## 🗂️ Struktura projektu

### 📁 Główne pliki Kotlin:

| Plik | Opis |
|------|------|
| **LoginActivity.kt** | Ekran logowania użytkownika (wcześniej MainActivity). Obsługuje logowanie po e-mailu i haśle. |
| **RegisterActivity.kt** | Ekran rejestracji nowego użytkownika — zapisuje dane w bazie Room. |
| **DashboardActivity.kt** | Główny ekran po zalogowaniu – wyświetla powitanie użytkownika (imię/nick) oraz nawigację do innych funkcji. |
| **AddExpenseActivity.kt** | Formularz dodawania nowego wydatku (kwota, kategoria, opis, notatka, data). |
| **HistoryActivity.kt** | Historia wydatków – obsługuje wyszukiwanie, sortowanie i (w kolejnych etapach) filtrowanie danych. |
| **ExpenseAdapter.kt** | Adapter RecyclerView do wyświetlania listy wydatków w historii. |
| **AppDatabase.kt** | Klasa konfigurująca lokalną bazę danych Room. |
| **ExpenseDao.kt** | DAO – metody do obsługi danych (insert, query, delete itp.). |
| **Expense.kt** | Model danych dla wydatku (id, userId, kwota, opis, kategoria, data, notatka). |
| **User.kt** | Model danych użytkownika (id, imię/nick, e-mail, hasło). |

---

### 📁 Layouty XML:

| Plik | Opis |
|------|------|
| **activity_login.xml** | Ekran logowania użytkownika. |
| **activity_register.xml** | Formularz rejestracji nowego konta. |
| **activity_dashboard.xml** | Główny ekran aplikacji po zalogowaniu. |
| **activity_add_expense.xml** | Ekran dodawania nowego wydatku. |
| **activity_history.xml** | Lista wydatków z wyszukiwarką, sortowaniem i w przyszłości filtrami. |
| **item_expense.xml** | Pojedynczy element listy wydatków wyświetlany w RecyclerView. |

---

## 🧩 Funkcjonalności

✅ Rejestracja i logowanie użytkowników  
✅ Dodawanie wydatków  
✅ Przeglądanie historii wydatków  
✅ Wyszukiwanie po opisie lub notatce  
✅ Sortowanie wydatków po dacie lub kwocie  
🔜 Filtrowanie (np. po kategorii, zakresie dat — etap w trakcie wdrażania)  
✅ Wylogowanie użytkownika  
✅ Wyświetlanie imienia/nicka użytkownika po zalogowaniu

---

## 🚀 Jak uruchomić projekt

1. Sklonuj repozytorium lub skopiuj projekt do swojego komputera:
   ```bash
   git clone https://github.com/twoje-repozytorium/HomeBudget.git