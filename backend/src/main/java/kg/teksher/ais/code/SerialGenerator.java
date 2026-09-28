package kg.teksher.ais.code;
import org.springframework.stereotype.Component; import java.security.SecureRandom; import java.util.*;
@Component public class SerialGenerator{
 private final SecureRandom random=new SecureRandom();
 public List<String> generate(int quantity,Set<String> existing){Set<String> out=new LinkedHashSet<>();while(out.size()<quantity){out.add(Long.toUnsignedString(random.nextLong(),36).substring(0,Math.min(13,Long.toUnsignedString(random.nextLong(),36).length())).toUpperCase(Locale.ROOT));}out.removeAll(existing);while(out.size()<quantity){out.add(UUID.randomUUID().toString().replace("-","").substring(0,13));}return new ArrayList<>(out);}
}