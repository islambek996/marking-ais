package kg.teksher.ais.textbook;
import jakarta.validation.Valid; import jakarta.validation.constraints.NotBlank; import org.springframework.web.bind.annotation.*; import java.time.LocalDate; import java.util.*;
@RestController @RequestMapping("/api/textbooks") public class TextbookController{
 private final TextbookService service; public TextbookController(TextbookService service){this.service=service;}
 public record CreateRequest(UUID participantId,@NotBlank String gtin,@NotBlank String title,String fullTitle,@NotBlank String author,@NotBlank String schoolClass,@NotBlank String subject,@NotBlank String publisher,LocalDate publicationDate,@NotBlank String language,@NotBlank String isbn,Integer printRun,String ageCategory,String countryOfProduction,String manufacturer,String description){}
 @GetMapping public List<Textbook> all(){return service.all();} @GetMapping("/{id}") public Textbook get(@PathVariable UUID id){return service.get(id);}
 @PostMapping public Textbook create(@Valid @RequestBody CreateRequest r){return service.create(r.participantId(),r.gtin(),r.title(),r.fullTitle(),r.author(),r.schoolClass(),r.subject(),r.publisher(),r.publicationDate(),r.language(),r.isbn(),r.printRun(),r.ageCategory(),r.countryOfProduction(),r.manufacturer(),r.description());}
 @PostMapping("/{id}/publish") public Textbook publish(@PathVariable UUID id){return service.publish(id);}
}