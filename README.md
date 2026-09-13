<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&color=b3f79c&height=220&section=header&text=rExpLevel&fontSize=72&fontAlignY=32&fontColor=1a1a1a&desc=опыт%20→%20бутылочки&descAlignY=52&descSize=22&descColor=2D3E27&animation=fadeIn" alt="rExpLevel"/>

<br/>

[![Paper](https://img.shields.io/badge/Paper-1.21.4+-00A98F?style=for-the-badge&logo=PaperMC&logoColor=white)](https://papermc.io/)
[![Folia](https://img.shields.io/badge/Folia-supported-9B59B6?style=for-the-badge)](https://papermc.io/software/folia)
[![Leaf](https://img.shields.io/badge/Leaf-compatible-2ECC71?style=for-the-badge)](https://www.leafmc.one/)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)

</div>

---

## 🖥️ Поддерживаемые ядра

| Ядро | Статус |
|:----:|:------:|
| **Paper** | ✅ |
| **Folia** | ✅ |
| **Leaf** | ✅ |
| **Purpur / Pufferfish** | ✅ |

---

## ✨ Возможности

| | |
|---|---|
| 🧪 | Кастомные XP-бутылочки с точным опытом в предмете |
| 🍾 | Упаковка опыта в обычные бутылочки опыта и в кастомные |
| ⚙️ | Автоконвертация опыта в кастомные бутылочки и бутылочки опыта |
| ⏱️ | Триггеры: **EVENT** — автоконвертация при получении любого опыта; **PERIODIC** — проверка опыта каждые N секунд (по умолчанию 3) |
| 🥤 | Возврат опыта: ПКМ (**DRINK**) или кидок (**THROW**) |
| 🍗 | Опциональный расход голода за бутылку |
| ⚡ | Высокая производительность: Folia-safe scheduler, тонкий jar, без лишней нагрузки |

---

## ⌨️ Команды

Право: `rexp.use` · по умолчанию `/rexp`, алиас `/rx`

| Команда | Описание |
|---------|----------|
| `/rexp` · `/rexp help` | 📖 Справка |
| `/rexp pack bottle <уровни> [N]` | 🧪 Создать кастомные бутылочки |
| `/rexp pack experience <XP\|all>` | 🍾 Создать обычные пузырьки |
| `/rexp auto <on\|off> <bottle\|experience>` | ⚙️ Вкл/выкл автоконвертацию |
| `/rexp give <игрок> <уровни> [N]` | 🎁 Выдать бутылочки *(op)* |
| `/rexp reload` | ♻️ Перезагрузить конфиги *(op)* |
| `/rexp update` | ⬆️ Обновление с GitHub *(op)* |

---

## 🔌 Placeholders

| Плейсхолдер | Значение |
|-------------|----------|
| `%rexp_auto%` | `on` / `off` |
| `%rexp_mode%` | `bottle` / `experience` |
