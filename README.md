# Marking AIS – MVP

Автоматизированная информационная система маркировки учебников.

## Что уже реализовано

### Backend
- Java 21 + Spring Boot
- In-Memory Repository
- участники
- группа товаров «Учебники»
- карточки учебников
- GTIN validation
- генерация серийных номеров
- GS1 payload
- DataMatrix ECC200 PNG в Base64
- жизненный цикл КМ: EMITTED → APPLIED → IN_CIRCULATION → WITHDRAWN
- заказы кодов
- Billing Interface
- Calculate → Reserve → Execute → Capture / Release
- Refund
- баланс, резерв и доступный остаток
- idempotency для резервирования

### Frontend
React + Vite. Frontend использует REST API backend через Vite proxy.

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

1. Полные операции нанесения, ввода в оборот, агрегации, разагрегации и вывода.
2. История операций и документы.
3. Полноценные пользователи и роли.
4. PostgreSQL + Flyway.
5. Полный Billing ledger, тарифы, платежи и сверка.
