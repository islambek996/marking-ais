package kg.teksher.ais.participant;
import jakarta.validation.Valid; import jakarta.validation.constraints.NotBlank; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/participants") public class ParticipantController{
 private final ParticipantService service; public ParticipantController(ParticipantService service){this.service=service;}
 public record CreateRequest(@NotBlank String inn,@NotBlank String name,String legalForm,String country,String legalAddress,String actualAddress,String phone,String email){}
 @GetMapping public List<Participant> all(){return service.all();} @GetMapping("/{id}") public Participant get(@PathVariable UUID id){return service.get(id);}
 @PostMapping public Participant create(@Valid @RequestBody CreateRequest r){return service.create(r.inn(),r.name(),r.legalForm(),r.country(),r.legalAddress(),r.actualAddress(),r.phone(),r.email());}
}