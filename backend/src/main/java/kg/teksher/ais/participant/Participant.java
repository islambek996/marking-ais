package kg.teksher.ais.participant;
import java.time.LocalDate; import java.util.UUID;
public record Participant(UUID id,String inn,String name,String legalForm,String country,String legalAddress,String actualAddress,String phone,String email,Status status,LocalDate registrationDate){
 public enum Status{ACTIVE,BLOCKED,INACTIVE}
}