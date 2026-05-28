# PocketClaw

Android-агент: чат-окно, локальная или внешняя LLM в ReAct-цикле, действия выполняются через AccessibilityService. **Каждое действие требует tap «Разрешить»** от юзера — никакого автопилота.

## Зачем
OpenInterpreter / Claude Code в кармане. Скажи «открой YouTube и найди X» — модель планирует, ты разрешаешь каждый тап.

## Стек
- Kotlin + Compose Material3 (monochrome)
- Hilt DI
- OkHttp + kotlinx.serialization (OpenAI-совместимый API: OpenAI / OpenRouter / Groq / PocketQwal / Custom)
- AccessibilityService для read_screen / tap / type / scroll
- DataStore для настроек

## Tools (ReAct loop)
- `open_url(url)` — открыть в браузере
- `launch_app(pkg)` — запустить приложение
- `tap_text(text)` — тап по элементу с текстом
- `tap_xy(x,y)` — тап в координаты
- `type(text)` — напечатать в активное поле
- `scroll(dir)` — прокрутить
- `read_screen()` — UI-дерево (не требует подтверждения)
- `wait(ms)` — пауза
- `done(summary)` — финальный ответ

## Безопасность
Confirm-every-action включён и не отключается. `read_screen` / `wait` / `done` проходят без диалога, всё остальное — нет.
