package kg.teksher.ais.billing;

import kg.teksher.ais.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Временная реализация Billing для MVP.
 *
 * Важно: это не финансовый ledger. Здесь реализован только минимальный
 * жизненный цикл операции: RESERVED -> CAPTURED/RELEASED -> REFUNDED.
 * На этапе PostgreSQL этот класс будет заменён полноценным Billing-хранилищем.
 */
@Service
public class InMemoryBillingService implements BillingService {
    private final Map<UUID, BigDecimal> balances = new ConcurrentHashMap<>();
    private final Map<UUID, BigDecimal> reserved = new ConcurrentHashMap<>();
    private final Map<String, UUID> idempotencyKeys = new ConcurrentHashMap<>();
    private final Map<UUID, BillingResult> operations = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> operationParticipants = new ConcurrentHashMap<>();

    @Override
    public BigDecimal calculate(String operation, int quantity) {
        // На MVP единая тестовая стоимость операции – 0,61 KGS за единицу.
        return new BigDecimal("0.61")
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public synchronized BillingResult reserve(
            UUID participantId,
            String operation,
            BigDecimal amount,
            String idempotencyKey) {

        if (idempotencyKey != null && idempotencyKeys.containsKey(idempotencyKey)) {
            return operations.get(idempotencyKeys.get(idempotencyKey));
        }

        BigDecimal balance = balances.getOrDefault(participantId, BigDecimal.ZERO);
        BigDecimal reservedAmount = reserved.getOrDefault(participantId, BigDecimal.ZERO);

        if (balance.subtract(reservedAmount).compareTo(amount) < 0) {
            throw new ApiException(
                    "INSUFFICIENT_BALANCE",
                    "Недостаточно средств для выполнения операции",
                    HttpStatus.BAD_REQUEST
            );
        }

        UUID operationId = UUID.randomUUID();
        BillingResult result = new BillingResult(operationId, "RESERVED", amount);

        operations.put(operationId, result);
        operationParticipants.put(operationId, participantId);
        reserved.put(participantId, reservedAmount.add(amount));

        if (idempotencyKey != null) {
            idempotencyKeys.put(idempotencyKey, operationId);
        }

        return result;
    }

    @Override
    public synchronized BillingResult capture(UUID operationId) {
        BillingResult current = getOperation(operationId);

        if (!"RESERVED".equals(current.status())) {
            return current;
        }

        UUID participantId = operationParticipants.get(operationId);
        releaseReservation(participantId, current.amount());

        balances.merge(participantId, current.amount().negate(), BigDecimal::add);

        BillingResult captured = new BillingResult(
                operationId,
                "CAPTURED",
                current.amount()
        );
        operations.put(operationId, captured);
        return captured;
    }

    @Override
    public synchronized BillingResult release(UUID operationId) {
        BillingResult current = getOperation(operationId);

        if (!"RESERVED".equals(current.status())) {
            return current;
        }

        UUID participantId = operationParticipants.get(operationId);
        releaseReservation(participantId, current.amount());

        BillingResult released = new BillingResult(
                operationId,
                "RELEASED",
                current.amount()
        );
        operations.put(operationId, released);
        return released;
    }

    @Override
    public synchronized BillingResult refund(UUID operationId) {
        BillingResult current = getOperation(operationId);

        if (!"CAPTURED".equals(current.status())) {
            return current;
        }

        UUID participantId = operationParticipants.get(operationId);
        balances.merge(participantId, current.amount(), BigDecimal::add);

        BillingResult refunded = new BillingResult(
                operationId,
                "REFUNDED",
                current.amount()
        );
        operations.put(operationId, refunded);
        return refunded;
    }

    @Override
    public Balance getBalance(UUID participantId) {
        BigDecimal balance = balances.getOrDefault(participantId, BigDecimal.ZERO);
        BigDecimal reservedAmount = reserved.getOrDefault(participantId, BigDecimal.ZERO);

        return new Balance(
                balance,
                reservedAmount,
                balance.subtract(reservedAmount),
                "KGS"
        );
    }

    public void deposit(UUID participantId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ApiException(
                    "INVALID_AMOUNT",
                    "Сумма пополнения должна быть больше нуля",
                    HttpStatus.BAD_REQUEST
            );
        }
        balances.merge(participantId, amount, BigDecimal::add);
    }

    private BillingResult getOperation(UUID operationId) {
        BillingResult result = operations.get(operationId);

        if (result == null) {
            throw new ApiException(
                    "BILLING_OPERATION_NOT_FOUND",
                    "Billing операция не найдена",
                    HttpStatus.NOT_FOUND
            );
        }

        return result;
    }

    private void releaseReservation(UUID participantId, BigDecimal amount) {
        BigDecimal current = reserved.getOrDefault(participantId, BigDecimal.ZERO);
        BigDecimal next = current.subtract(amount);

        if (next.signum() <= 0) {
            reserved.remove(participantId);
        } else {
            reserved.put(participantId, next);
        }
    }
}
