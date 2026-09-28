package kg.teksher.ais.participant;
import kg.teksher.ais.common.ApiException; import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import java.time.LocalDate; import java.util.*;
@Service public class ParticipantService{
 private final ParticipantRepository repo; public ParticipantService(ParticipantRepository repo){this.repo=repo;}
 public List<Participant> all(){return repo.findAll();} public Participant get(UUID id){return repo.findById(id).orElseThrow(()->new ApiException("PARTICIPANT_NOT_FOUND","Участник не найден",HttpStatus.NOT_FOUND));}
 public Participant create(String inn,String name,String legalForm,String country,String legalAddress,String actualAddress,String phone,String email){
  if(inn==null||inn.isBlank()||name==null||name.isBlank())throw new ApiException("VALIDATION_ERROR","ИНН и наименование обязательны",HttpStatus.BAD_REQUEST);
  return repo.save(new Participant(UUID.randomUUID(),inn,name,legalForm,country,legalAddress,actualAddress,phone,email,Participant.Status.ACTIVE,LocalDate.now()));
 }
}