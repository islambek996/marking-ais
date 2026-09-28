package kg.teksher.ais.common;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;
import java.util.Map;
@RestControllerAdvice
public class GlobalExceptionHandler {
 @ExceptionHandler(ApiException.class) ResponseEntity<?> api(ApiException e){return ResponseEntity.status(e.getStatus()).body(Map.of("code",e.getCode(),"message",e.getMessage(),"timestamp",OffsetDateTime.now().toString()));}
 @ExceptionHandler(Exception.class) ResponseEntity<?> other(Exception e){return ResponseEntity.internalServerError().body(Map.of("code","INTERNAL_ERROR","message",e.getMessage()==null?"Внутренняя ошибка":e.getMessage(),"timestamp",OffsetDateTime.now().toString()));}
}