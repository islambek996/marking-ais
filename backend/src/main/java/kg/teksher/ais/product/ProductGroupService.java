package kg.teksher.ais.product;
import org.springframework.stereotype.Service; import java.util.*; import java.util.concurrent.ConcurrentHashMap;
@Service public class ProductGroupService{
 private final Map<UUID,ProductGroup> data=new ConcurrentHashMap<>();
 public ProductGroupService(){UUID id=UUID.randomUUID();data.put(id,new ProductGroup(id,"TEXTBOOKS","Учебники","Учебники",ProductGroup.Status.ACTIVE,List.of("Название учебника","Автор","Класс","Предмет","Издательство","Год издания","Язык","ISBN")));}
 public List<ProductGroup> all(){return new ArrayList<>(data.values());}
}