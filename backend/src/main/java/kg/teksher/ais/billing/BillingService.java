package kg.teksher.ais.billing;
import java.math.BigDecimal; import java.util.UUID;
public interface BillingService{
 BigDecimal calculate(String operation,int quantity); BillingResult reserve(UUID participantId,String operation,BigDecimal amount,String idempotencyKey); BillingResult capture(UUID operationId); BillingResult release(UUID operationId); BillingResult refund(UUID operationId); Balance getBalance(UUID participantId);
 record BillingResult(UUID operationId,String status,BigDecimal amount){} record Balance(BigDecimal balance,BigDecimal reservedBalance,BigDecimal availableBalance,String currency){}
}