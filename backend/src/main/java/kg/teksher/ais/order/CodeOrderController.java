package kg.teksher.ais.order;
import jakarta.validation.Valid;import jakarta.validation.constraints.Min;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequestMapping("/api/code-orders")public class CodeOrderController{
 private final CodeOrderService service;public CodeOrderController(CodeOrderService service){this.service=service;}
 public record CreateRequest(UUID participantId,UUID textbookId,@Min(1)int quantity){}
 @GetMapping public List<CodeOrder> all(){return service.all();}@PostMapping public CodeOrder create(@Valid @RequestBody CreateRequest r){return service.create(r.participantId(),r.textbookId(),r.quantity());}
}