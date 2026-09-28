# Marking AIS – MVP

Автоматизированная информационная система маркировки учебников.

## Backend
- Java 21 + Spring Boot
- In-Memory Repository
- участники
- группа товаров «Учебники»
- карточки учебников
- GTIN validation
- генерация серийных номеров
- GS1 payload
- DataMatrix ECC200
- жизненный цикл КМ: EMITTED → APPLIED → IN_CIRCULATION → WITHDRAWN
- заказы КМ
- Billing Interface
- Calculate → Reserve → Execute → Capture / Release
- Refund
- баланс, резерв и доступный остаток
- idempotency

## Frontend
React + Vite, REST API через Vite proxy.

## Сканер DataMatrix
В каталоге `scanner` находится отдельное React-приложение для проверки кодов маркировки.
- сканирование DataMatrix камерой смартфона;
- разбор GS1 AI 01 (GTIN) и AI 21 (серийный номер);
- проверка кода через публичный API AIS;
- вывод информации об учебнике, владельце и истории кода;
- резервный режим проверки содержимого DataMatrix вручную;
- сканирование DataMatrix с фотографии.

Запуск сканера:
```bash
cd scanner
npm install
npm run dev
```

Сканер: http://localhost:5173

> Для текущего MVP DataMatrix генерируется локально. Такой код является тестовым и не считается официальным государственным кодом маркировки.

## Запуск
Backend:
```bash
cd backend
mvn spring-boot:run
```

Frontend:
```bash
cd frontend
npm install
npm run dev
```

Backend: http://localhost:8080
Frontend: http://localhost:5173

## Следующие этапы
1. Нанесение, ввод в оборот, агрегация, разагрегация, вывод из оборота.
2. Документы и история.
3. Пользователи и роли.
4. PostgreSQL + Flyway.
5. Полный Billing ledger, тарифы, платежи и сверка.
