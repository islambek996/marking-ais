package kg.teksher.ais.billing;
import org.springframework.web.bind.annotation.*; import java.math.BigDecimal; import java.util.UUID;
@RestController @RequestMapping("/api/billing") public class BillingController{
 private final BillingService billing; private final InMemoryBillingService memory; public BillingController(BillingService billing,InMemoryBillingService memory){this.billing=billing;this.memory=memory;}
 @GetMapping("/balance/{participantId}") public BillingService.Balance balance(@PathVariable UUID participantId){return billing.getBalance(participantId);}
 @PostMapping("/deposit/{participantId}") public BillingService.Balance deposit(@PathVariable UUID participantId,@RequestParam BigDecimal amount){memory.deposit(participantId,amount);return billing.getBalance(participantId);}
}