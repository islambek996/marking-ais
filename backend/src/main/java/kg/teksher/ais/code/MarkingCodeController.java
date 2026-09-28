package kg.teksher.ais.code;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/marking-codes") public class MarkingCodeController{
 private final MarkingCodeService service;public MarkingCodeController(MarkingCodeService service){this.service=service;}
 @GetMapping public List<MarkingCode> all(){return service.all();}@GetMapping("/{id}") public MarkingCode get(@PathVariable UUID id){return service.get(id);}
 @PostMapping("/{id}/apply") public MarkingCode apply(@PathVariable UUID id){return service.transition(id,MarkingCode.Status.APPLIED);}
 @PostMapping("/{id}/circulate") public MarkingCode circulate(@PathVariable UUID id){return service.transition(id,MarkingCode.Status.IN_CIRCULATION);}
 @PostMapping("/{id}/withdraw") public MarkingCode withdraw(@PathVariable UUID id){return service.transition(id,MarkingCode.Status.WITHDRAWN);}
}