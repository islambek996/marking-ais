package kg.teksher.ais.textbook;
import org.springframework.stereotype.Repository; import java.util.*; import java.util.concurrent.ConcurrentHashMap;
@Repository public class InMemoryTextbookRepository implements TextbookRepository{
 private final Map<UUID,Textbook> data=new ConcurrentHashMap<>();
 public Textbook save(Textbook t){data.put(t.id(),t);return t;} public Optional<Textbook> findById(UUID id){return Optional.ofNullable(data.get(id));} public List<Textbook> findAll(){return new ArrayList<>(data.values());}
 public boolean existsByGtin(String gtin){return data.values().stream().anyMatch(t->t.gtin().equals(gtin));}
}