package kg.teksher.ais.product;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/product-groups") public class ProductGroupController{
 private final ProductGroupService service; public ProductGroupController(ProductGroupService service){this.service=service;} @GetMapping public List<ProductGroup> all(){return service.all();}
}