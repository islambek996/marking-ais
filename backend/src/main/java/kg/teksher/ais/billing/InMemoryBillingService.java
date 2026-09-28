package kg.teksher.ais.billing;
import kg.teksher.ais.common.ApiException; import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import java.math.*; import java.util.*; import java.util.concurrent.ConcurrentHashMap;
@Service public class InMemoryBillingService implements BillingService{
 private final Map<UUID,BigDecimal> balances=new ConcurrentHashMap<>(),reserved=new ConcurrentHashMap<>(); private final Map<String,UUID> keys=new ConcurrentHashMap<>(); private final Map<UUID,BillingResult> ops=new ConcurrentHashMap<>();
 public BigDecimal calculate(String operation,int quantity){return new BigDecimal("0.61").multiply(BigDecimal.valueOf(quantity)).setScale(2,RoundingMode.HALF_UP);}
 public BillingResult reserve(UUID participantId,String operation,BigDecimal amount,String key){
  if(key!=null&&keys.containsKey(key))return ops.get(keys.get(key)); BigDecimal b=balances.getOrDefault(participantId,BigDecimal.ZERO),r=reserved.getOrDefault(participantId,BigDecimal.ZERO);
  if(b.subtract(r).compareTo(amount)<0)throw new ApiException("INSUFFICIENT_BALANCE","Недостаточно средств для выполнения операции",HttpStatus.BAD_REQUEST);
  UUID id=UUID.randomUUID();var x=new BillingResult(id,"RESERVED",amount);ops.put(id,x);reserved.put(participantId,r.add(amount));if(key!=null)keys.put(key,id);return x;
 }
 public BillingResult capture(UUID id){return change(id,"CAPTURED");} public BillingResult release(UUID id){return change(id,"RELEASED");} public BillingResult refund(UUID id){return change(id,"REFUNDED");}
 private BillingResult change(UUID id,String status){var x=ops.get(id);if(x==null)throw new ApiException("BILLING_OPERATION_NOT_FOUND","Billing операция не найдена",HttpStatus.NOT_FOUND);if(!x.status().equals("RESERVED"))return x;ops.put(id,new BillingResult(id,status,x.amount()));return ops.get(id);}
 public Balance getBalance(UUID id){var b=balances.getOrDefault(id,BigDecimal.ZERO);var r=reserved.getOrDefault(id,BigDecimal.ZERO);return new Balance(b,r,b.subtract(r),"KGS");}
 public void deposit(UUID id,BigDecimal amount){balances.merge(id,amount,BigDecimal::add);}
}