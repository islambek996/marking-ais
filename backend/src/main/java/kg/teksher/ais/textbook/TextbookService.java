package kg.teksher.ais.textbook;
import kg.teksher.ais.common.ApiException; import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import java.time.LocalDate; import java.util.*;
@Service public class TextbookService{
 private final TextbookRepository repo; public TextbookService(TextbookRepository repo){this.repo=repo;}
 public List<Textbook> all(){return repo.findAll();} public Textbook get(UUID id){return repo.findById(id).orElseThrow(()->new ApiException("TEXTBOOK_NOT_FOUND","Карточка учебника не найдена",HttpStatus.NOT_FOUND));}
 public Textbook create(UUID participantId,String gtin,String title,String fullTitle,String author,String schoolClass,String subject,String publisher,LocalDate date,String language,String isbn,Integer printRun,String age,String country,String manufacturer,String description){
  if(!GtinValidator.valid(gtin))throw new ApiException("INVALID_GTIN","Некорректный GTIN",HttpStatus.BAD_REQUEST); if(repo.existsByGtin(gtin))throw new ApiException("GTIN_ALREADY_EXISTS","GTIN уже используется",HttpStatus.CONFLICT);
  if(title==null||author==null||schoolClass==null||subject==null||publisher==null||language==null||isbn==null)throw new ApiException("VALIDATION_ERROR","Обязательные характеристики учебника не заполнены",HttpStatus.BAD_REQUEST);
  return repo.save(new Textbook(UUID.randomUUID(),participantId,gtin,title,fullTitle,author,schoolClass,subject,publisher,date,language,isbn,printRun,age,country,manufacturer,description,Textbook.Status.DRAFT,LocalDate.now()));
 }
 public Textbook publish(UUID id){var t=get(id);return repo.save(new Textbook(t.id(),t.participantId(),t.gtin(),t.title(),t.fullTitle(),t.author(),t.schoolClass(),t.subject(),t.publisher(),t.publicationDate(),t.language(),t.isbn(),t.printRun(),t.ageCategory(),t.countryOfProduction(),t.manufacturer(),t.description(),Textbook.Status.PUBLISHED,t.createdAt()));}
}