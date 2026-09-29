package kg.teksher.ais.billing;

import java.math.BigDecimal;
import java.util.UUID;

public class BillingController {
    private final BillingService billing;
    private final InMemoryBillingService memory;

    public BillingController(BillingService billing, InMemoryBillingService memory) {
        this.billing = billing;
        this.memory = memory;
    }

    public BillingService.Balance deposit(UUID participantId, BigDecimal amount) {
        memory.deposit(participantId, amount);
        return billing.getBalance(participantId);
    }
}
