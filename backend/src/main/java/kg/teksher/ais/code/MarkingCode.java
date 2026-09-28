package kg.teksher.ais.code;
import java.time.OffsetDateTime; import java.util.UUID;
public record MarkingCode(UUID id,UUID textbookId,UUID participantId,String gtin,String serial,String gs1Payload,String dataMatrix,Status status,OffsetDateTime createdAt){
 public enum Status{EMITTED,APPLIED,IN_CIRCULATION,WITHDRAWN}
}