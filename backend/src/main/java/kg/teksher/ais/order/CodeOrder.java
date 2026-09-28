package kg.teksher.ais.order;
import java.math.BigDecimal;import java.time.OffsetDateTime;import java.util.UUID;
public record CodeOrder(UUID id,UUID participantId,UUID textbookId,String gtin,int quantity,BigDecimal amount,UUID billingOperationId,Status status,OffsetDateTime createdAt){public enum Status{CREATED,PROCESSING,COMPLETED,PARTIALLY_COMPLETED,CANCELLED,FAILED}}