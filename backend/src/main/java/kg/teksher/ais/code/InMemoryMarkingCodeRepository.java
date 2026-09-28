package kg.teksher.ais.code;
import org.springframework.stereotype.Repository; import java.util.*; import java.util.concurrent.ConcurrentHashMap;
@Repository public class InMemoryMarkingCodeRepository implements MarkingCodeRepository{
 private final Map<UUID,MarkingCode> data=new ConcurrentHashMap<>(); public MarkingCode save(MarkingCode c){data.put(c.id(),c);return c;} public List<MarkingCode> all(){return new ArrayList<>(data.values());} public Optional<MarkingCode> findById(UUID id){return Optional.ofNullable(data.get(id));} public boolean exists(String gtin,String serial){return data.values().stream().anyMatch(c->c.gtin().equals(gtin)&&c.serial().equals(serial));}
}