package kg.teksher.ais.order;

import kg.teksher.ais.billing.BillingService;
import kg.teksher.ais.code.MarkingCodeService;
import kg.teksher.ais.textbook.TextbookService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class CodeOrderService {
    private final TextbookService textbooks;
    private final MarkingCodeService codes;
    private final BillingService billing;
    private final Map<UUID, CodeOrder> orders = new LinkedHashMap<>();

    public CodeOrderService(TextbookService t, MarkingCodeService c, BillingService b) {
        textbooks = t;
        codes = c;
        billing = b;
    }

    public synchronized CodeOrder create(UUID participantId, UUID textbookId, int quantity) {
        var t = textbooks.get(textbookId);
        var amount = billing.calculate("MARKING_CODES_GENERATION", quantity);
        var r = billing.reserve(participantId, "MARKING_CODES_GENERATION", amount, UUID.randomUUID().toString());
        var id = UUID.randomUUID();
        var start = new CodeOrder(id, participantId, textbookId, t.gtin(), quantity, amount, r.operationId(), CodeOrder.Status.PROCESSING, OffsetDateTime.now());
        orders.put(id, start);
        try {
            codes.generate(t, participantId, quantity);
            billing.capture(r.operationId());
            var done = new CodeOrder(id, participantId, textbookId, t.gtin(), quantity, amount, r.operationId(), CodeOrder.Status.COMPLETED, OffsetDateTime.now());
            orders.put(id, done);
            return done;
        } catch (RuntimeException e) {
            billing.release(r.operationId());
            orders.put(id, new CodeOrder(id, participantId, textbookId, t.gtin(), quantity, amount, r.operationId(), CodeOrder.Status.FAILED, start.createdAt()));
            throw e;
        }
    }

    public List<CodeOrder> all() {
        return new ArrayList<>(orders.values());
    }
}