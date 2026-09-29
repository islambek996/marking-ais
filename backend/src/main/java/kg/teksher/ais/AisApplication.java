package kg.teksher.ais;

import com.google.zxing.*;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.datamatrix.DataMatrixWriter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication
public class AisApplication {
    public static void main(String[] args) {
        SpringApplication.run(AisApplication.class, args);
    }
}

record Participant(UUID id, String inn, String name, String legalForm, String country, String legalAddress,
                   String actualAddress, String phone, String email, String status, OffsetDateTime createdAt) {
}

record Textbook(UUID id, UUID participantId, String gtin, String title, String fullTitle, String author,
                String schoolClass, String subject, String publisher, Integer year, String language, String isbn,
                Integer printRun, String ageCategory, String countryOfProduction, String manufacturer,
                String description, String status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}

record MarkingCode(UUID id, UUID participantId, UUID textbookId, String gtin, String serial, String payload,
                   String dataMatrixBase64, String status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}

record CodeOrder(UUID id, UUID participantId, UUID textbookId, String gtin, int quantity, BigDecimal amount,
                 UUID billingOperationId, String status, OffsetDateTime createdAt) {
}

record Operation(UUID id, String type, UUID participantId, UUID textbookId, List<UUID> codeIds, int quantity,
                 String status, String reason, UUID billingOperationId, OffsetDateTime createdAt) {
}

record Document(UUID id, String number, String type, UUID participantId, String status, OffsetDateTime createdAt,
                UUID operationId, UUID billingOperationId) {
}

record History(UUID id, UUID codeId, String operation, String oldStatus, String newStatus, UUID participantId,
               UUID documentId, UUID operationId, UUID billingOperationId, OffsetDateTime createdAt) {
}

record User(UUID id, String fullName, String login, UUID participantId, String role, String status,
            OffsetDateTime createdAt) {
}

record BillingOperation(UUID id, UUID participantId, String type, BigDecimal amount, String status,
                        OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}

record Balance(BigDecimal balance, BigDecimal reservedBalance, BigDecimal availableBalance, String currency) {
}

record Aggregate(UUID id, String sscc, List<UUID> codeIds, String status, OffsetDateTime createdAt) {
}

class AisException extends RuntimeException {

    final String code;
    final HttpStatus status;

    AisException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }
}

@org.springframework.stereotype.Component
class AuthFilter extends OncePerRequestFilter {
    private final AisService s;

    AuthFilter(AisService s) {
        this.s = s;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, java.io.IOException {
        String path = req.getRequestURI(), method = req.getMethod();
        if (!path.startsWith("/api/") || path.equals("/api/auth/login") || (method.equals("GET") && path.equals("/api/marking-codes/lookup"))) {
            chain.doFilter(req, res);
            return;
        }
        String h = req.getHeader("Authorization");
        if (h == null || !h.startsWith("Bearer ")) {
            res.setStatus(401);
            res.setContentType("application/json");
            res.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Требуется авторизация\"}");
            return;
        }
        try {
            User u = s.currentUser(h.substring(7));
            req.setAttribute("currentUser", u);
            if (!allowed(u, method, path)) {
                res.setStatus(403);
                res.setContentType("application/json");
                res.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"Недостаточно прав для операции\"}");
                return;
            }
            chain.doFilter(req, res);
        } catch (AisException e) {
            res.setStatus(e.status.value());
            res.setContentType("application/json");
            res.getWriter().write("{\"code\":\"" + e.code + "\",\"message\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}");
        }
    }

    private boolean allowed(User u, String method, String path) {
        if (u.role().equals("ADMIN")) return true;
        if (u.role().equals("USER")) {
            if (method.equals("GET") && path.matches("/api/participants/[^/]+") && u.participantId() != null && path.endsWith(u.participantId().toString()))
                return true;
            if (method.equals("GET") && !path.startsWith("/api/participants") && !path.equals("/api/users"))
                return true;
            if (method.equals("POST") && (
                    path.equals("/api/textbooks") ||
                            path.matches("/api/textbooks/[^/]+/publish") ||
                            path.equals("/api/code-orders") ||
                            path.equals("/api/marking-codes/generate") ||
                            path.matches("/api/marking-codes/[^/]+/(apply|circulate|withdraw)") ||
                            path.matches("/api/operations/(marking|introduction|withdrawal)")
            )) return true;
        }
        return false;
    }
}

@RestControllerAdvice
class Errors {
    @ExceptionHandler(AisException.class)
    org.springframework.http.ResponseEntity<Map<String, Object>> api(AisException e) {
        return org.springframework.http.ResponseEntity.status(e.status).body(Map.of("code", e.code, "message", e.getMessage(), "timestamp", OffsetDateTime.now().toString()));
    }

    @ExceptionHandler(Exception.class)
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    Map<String, Object> other(Exception e) {
        return Map.of("code", "INTERNAL_ERROR", "message", e.getMessage() == null ? "Внутренняя ошибка" : e.getMessage(), "timestamp", OffsetDateTime.now().toString());
    }
}

@org.springframework.stereotype.Service
class AisService {
    final Map<UUID, Participant> participants = new ConcurrentHashMap<>();
    final Map<UUID, Textbook> textbooks = new ConcurrentHashMap<>();
    final Map<UUID, MarkingCode> codes = new ConcurrentHashMap<>();
    final Map<UUID, CodeOrder> orders = new ConcurrentHashMap<>();
    final Map<UUID, Operation> operations = new ConcurrentHashMap<>();
    final Map<UUID, Document> documents = new ConcurrentHashMap<>();
    final Map<UUID, History> history = new ConcurrentHashMap<>();
    final Map<UUID, User> users = new ConcurrentHashMap<>();
    final Map<UUID, BillingOperation> billing = new ConcurrentHashMap<>();
    final Map<UUID, BigDecimal> balances = new ConcurrentHashMap<>();
    final Map<UUID, BigDecimal> reserved = new ConcurrentHashMap<>();
    final Map<UUID, Aggregate> aggregates = new ConcurrentHashMap<>();
    final Map<UUID, UUID> codeBilling = new ConcurrentHashMap<>();
    final Map<String, UUID> gtins = new ConcurrentHashMap<>();
    final Map<UUID, String> passwords = new ConcurrentHashMap<>();
    final Map<String, UUID> sessions = new ConcurrentHashMap<>();

    @PostConstruct
    void seedUsers() {
        if (users.isEmpty()) {
            var now = OffsetDateTime.now();
            UUID adminId = UUID.randomUUID();
            users.put(adminId, new User(adminId, "Администратор AIS", "admin", null, "ADMIN", "ACTIVE", now));
            passwords.put(adminId, "admin");
            UUID participantId = UUID.randomUUID();
            participants.put(participantId, new Participant(participantId, "00000000000000", "Демо-участник", "ОсОО", "Кыргызская Республика", "", "", "", "", "ACTIVE", now));
            balances.put(participantId, BigDecimal.ZERO);
            UUID userId = UUID.randomUUID();
            users.put(userId, new User(userId, "Пользователь Демо", "user", participantId, "USER", "ACTIVE", now));
            passwords.put(userId, "user");
        }
    }

    String login(String login, String password) {
        for (var e : users.entrySet()) {
            var u = e.getValue();
            if (u.login().equals(login) && passwords.getOrDefault(e.getKey(), "").equals(password) && u.status().equals("ACTIVE")) {
                String token = UUID.randomUUID().toString();
                sessions.put(token, e.getKey());
                return token;
            }
        }
        throw error("INVALID_CREDENTIALS", "Неверный логин или пароль", HttpStatus.UNAUTHORIZED);
    }

    User currentUser(String token) {
        UUID id = sessions.get(token);
        if (id == null) throw error("UNAUTHORIZED", "Требуется авторизация", HttpStatus.UNAUTHORIZED);
        return users.get(id);
    }

    User requestUser(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer "))
            throw error("UNAUTHORIZED", "Требуется авторизация", HttpStatus.UNAUTHORIZED);
        return currentUser(authorization.substring(7));
    }

    boolean owns(User u, UUID participantId) {
        return u.role().equals("ADMIN") || (u.participantId() != null && u.participantId().equals(participantId));
    }

    AisException error(String c, String m, HttpStatus s) {
        return new AisException(c, m, s);
    }

    Participant participant(UUID id) {
        var p = participants.get(id);
        if (p == null) throw error("PARTICIPANT_NOT_FOUND", "Участник не найден", HttpStatus.NOT_FOUND);
        return p;
    }

    Textbook textbook(UUID id) {
        var t = textbooks.get(id);
        if (t == null) throw error("TEXTBOOK_NOT_FOUND", "Карточка учебника не найдена", HttpStatus.NOT_FOUND);
        return t;
    }

    MarkingCode code(UUID id) {
        var c = codes.get(id);
        if (c == null) throw error("CODE_NOT_FOUND", "Код маркировки не найден", HttpStatus.NOT_FOUND);
        return c;
    }

    BillingOperation bill(UUID id) {
        var b = billing.get(id);
        if (b == null) throw error("BILLING_OPERATION_NOT_FOUND", "Billing операция не найдена", HttpStatus.NOT_FOUND);
        return b;
    }

    boolean validGtin(String g) {
        if (g == null || !g.matches("\\d{8}|\\d{12}|\\d{13}|\\d{14}")) return false;
        int sum = 0, pos = 0;
        for (int i = g.length() - 2; i >= 0; i--, pos++) {
            int n = g.charAt(i) - 48;
            sum += n * (pos % 2 == 0 ? 3 : 1);
        }
        return (10 - sum % 10) % 10 == g.charAt(g.length() - 1) - 48;
    }

    BigDecimal price(int q) {
        return BigDecimal.valueOf(q).multiply(new BigDecimal("1.00")).setScale(2, RoundingMode.HALF_UP);
    }

    Balance balance(UUID id) {
        participant(id);
        BigDecimal b = balances.getOrDefault(id, BigDecimal.ZERO), r = reserved.getOrDefault(id, BigDecimal.ZERO);
        return new Balance(b, r, b.subtract(r), "KGS");
    }

    synchronized BillingOperation reserve(UUID p, String type, BigDecimal amount) {
        balance(p);
        BigDecimal b = balances.getOrDefault(p, BigDecimal.ZERO), r = reserved.getOrDefault(p, BigDecimal.ZERO);
        if (b.subtract(r).compareTo(amount) < 0)
            throw error("INSUFFICIENT_BALANCE", "Недостаточно средств для выполнения операции", HttpStatus.BAD_REQUEST);
        UUID id = UUID.randomUUID();
        var now = OffsetDateTime.now();
        var op = new BillingOperation(id, p, type, amount, "RESERVED", now, now);
        billing.put(id, op);
        reserved.put(p, r.add(amount));
        return op;
    }

    synchronized BillingOperation capture(UUID id) {
        var o = bill(id);
        if (!o.status().equals("RESERVED")) return o;
        reserved.put(o.participantId(), reserved.getOrDefault(o.participantId(), BigDecimal.ZERO).subtract(o.amount()).max(BigDecimal.ZERO));
        balances.put(o.participantId(), balances.getOrDefault(o.participantId(), BigDecimal.ZERO).subtract(o.amount()));
        return updateBill(o, "CAPTURED");
    }

    synchronized BillingOperation release(UUID id) {
        var o = bill(id);
        if (!o.status().equals("RESERVED")) return o;
        reserved.put(o.participantId(), reserved.getOrDefault(o.participantId(), BigDecimal.ZERO).subtract(o.amount()).max(BigDecimal.ZERO));
        return updateBill(o, "RELEASED");
    }

    synchronized BillingOperation refund(UUID id) {
        var o = bill(id);
        if (!o.status().equals("CAPTURED")) return o;
        balances.merge(o.participantId(), o.amount(), BigDecimal::add);
        return updateBill(o, "REFUNDED");
    }

    BillingOperation updateBill(BillingOperation o, String status) {
        var n = new BillingOperation(o.id(), o.participantId(), o.type(), o.amount(), status, o.createdAt(), OffsetDateTime.now());
        billing.put(o.id(), n);
        return n;
    }

    String serial() {
        while (true) {
            String candidate = UUID.randomUUID().toString().replace("-", "").substring(0, 13).toUpperCase();
            if (codes.values().stream().noneMatch(c -> c.serial().equals(candidate))) return candidate;
        }
    }

    String dataMatrix(String payload) {
        try {
            BitMatrix m = new DataMatrixWriter().encode(payload, BarcodeFormat.DATA_MATRIX, 280, 280, Map.of(EncodeHintType.MARGIN, 2));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(m, "PNG", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            throw error("DATAMATRIX_ERROR", "Не удалось создать DataMatrix", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    List<MarkingCode> generate(UUID participantId, UUID textbookId, int quantity) {
        var t = textbook(textbookId);
        participant(participantId);
        List<MarkingCode> result = new ArrayList<>();
        for (int i = 0; i < quantity; i++) {
            String serial = serial(), payload = "01" + t.gtin() + "21" + serial;
            var now = OffsetDateTime.now();
            var c = new MarkingCode(UUID.randomUUID(), participantId, textbookId, t.gtin(), serial, payload, dataMatrix(payload), "EMITTED", now, now);
            codes.put(c.id(), c);
            result.add(c);
        }
        return result;
    }

    void history(MarkingCode c, String operation, String oldStatus, String newStatus, UUID doc, UUID operationId, UUID billingOperationId) {
        UUID id = UUID.randomUUID();
        history.put(id, new History(id, c.id(), operation, oldStatus, newStatus, c.participantId(), doc, operationId, billingOperationId, OffsetDateTime.now()));
    }

    MarkingCode transition(UUID id, String target, String operation) {
        var c = code(id);
        boolean ok = (c.status().equals("EMITTED") && target.equals("APPLIED")) || (c.status().equals("APPLIED") && target.equals("IN_CIRCULATION")) || (c.status().equals("IN_CIRCULATION") && target.equals("WITHDRAWN"));
        if (!ok) throw error("INVALID_STATUS_TRANSITION", "Недопустимый переход статуса кода", HttpStatus.BAD_REQUEST);
        var n = new MarkingCode(c.id(), c.participantId(), c.textbookId(), c.gtin(), c.serial(), c.payload(), c.dataMatrixBase64(), target, c.createdAt(), OffsetDateTime.now());
        codes.put(id, n);
        history(n, operation, c.status(), target, null, null, codeBilling.get(id));
        return n;
    }

    String docNumber(String type) {
        return type + "-" + OffsetDateTime.now().toLocalDate() + "-" + (documents.size() + 1);
    }
}

@RestController
@RequestMapping("/api/participants")
class ParticipantsApi {
    final AisService s;

    ParticipantsApi(AisService s) {
        this.s = s;
    }

    record Req(@NotBlank String inn, @NotBlank String name, String legalForm, String country, String legalAddress,
               String actualAddress, String phone, String email, String login, String password) {
    }

    @GetMapping
    List<Participant> all() {
        return new ArrayList<>(s.participants.values());
    }

    @GetMapping("/{id}")
    Participant get(@PathVariable UUID id) {
        return s.participant(id);
    }

    @PostMapping
    Participant create(@Valid @RequestBody Req r) {
        if (r.login() == null || r.login().isBlank() || r.password() == null || r.password().isBlank())
            throw s.error("INVALID_ACCOUNT", "Для участника необходимо указать логин и пароль", HttpStatus.BAD_REQUEST);
        if (s.users.values().stream().anyMatch(u -> u.login().equalsIgnoreCase(r.login())))
            throw s.error("LOGIN_ALREADY_EXISTS", "Логин уже используется", HttpStatus.CONFLICT);
        UUID id = UUID.randomUUID();
        var now = OffsetDateTime.now();
        var p = new Participant(id, r.inn(), r.name(), r.legalForm(), r.country(), r.legalAddress(), r.actualAddress(), r.phone(), r.email(), "ACTIVE", now);
        s.participants.put(id, p);
        s.balances.put(id, BigDecimal.ZERO);
        return p;
    }

    @PostMapping("/{id}/balance")
    Balance deposit(@PathVariable UUID id, @RequestParam BigDecimal amount) {
        s.participant(id);
        if (amount == null || amount.signum() <= 0)
            throw s.error("INVALID_AMOUNT", "Сумма должна быть больше нуля", HttpStatus.BAD_REQUEST);
        s.balances.merge(id, amount, BigDecimal::add);
        return s.balance(id);
    }

    @GetMapping("/{id}/balance")
    Balance balance(@PathVariable UUID id) {
        return s.balance(id);
    }
}

@RestController
@RequestMapping("/api/product-groups")
class ProductGroupsApi {
    @GetMapping
    List<Map<String, Object>> all() {
        return List.of(Map.of("id", "TEXTBOOKS", "code", "TEXTBOOKS", "name", "Учебники", "status", "ACTIVE", "requiredCharacteristics", List.of("Название учебника", "Автор", "Класс", "Предмет", "Издательство", "Год издания", "Язык", "ISBN")));
    }
}

@RestController
@RequestMapping("/api/textbooks")
class TextbooksApi {
    final AisService s;

    TextbooksApi(AisService s) {
        this.s = s;
    }

    record Req(UUID participantId, @NotBlank String gtin, @NotBlank String title, String fullTitle,
               @NotBlank String author, @NotBlank String schoolClass, @NotBlank String subject,
               @NotBlank String publisher, Integer year, @NotBlank String language, @NotBlank String isbn,
               Integer printRun, String ageCategory, String countryOfProduction, String manufacturer,
               String description) {
    }

    @GetMapping
    List<Textbook> all(@RequestHeader("Authorization") String authorization) {
        User u = s.requestUser(authorization);
        return s.textbooks.values().stream().filter(t -> s.owns(u, t.participantId())).toList();
    }

    @GetMapping("/{id}")
    Textbook get(@PathVariable UUID id, @RequestHeader("Authorization") String authorization) {
        var t = s.textbook(id);
        if (!s.owns(s.requestUser(authorization), t.participantId()))
            throw s.error("FORBIDDEN", "Карточка принадлежит другому участнику", HttpStatus.FORBIDDEN);
        return t;
    }

    @PostMapping
    Textbook create(@Valid @RequestBody Req r, @RequestHeader("Authorization") String authorization) {
        User u = s.requestUser(authorization);
        if (u.role().equals("USER") && (u.participantId() == null || !u.participantId().equals(r.participantId())))
            throw s.error("FORBIDDEN", "Пользователь может создавать карточки только от своего участника", HttpStatus.FORBIDDEN);
        if (!s.validGtin(r.gtin())) throw s.error("INVALID_GTIN", "Некорректный GTIN", HttpStatus.BAD_REQUEST);
        if (s.gtins.containsKey(r.gtin()))
            throw s.error("GTIN_ALREADY_EXISTS", "GTIN уже используется", HttpStatus.CONFLICT);
        if (r.participantId() != null) s.participant(r.participantId());
        UUID id = UUID.randomUUID();
        s.gtins.put(r.gtin(), id);
        var now = OffsetDateTime.now();
        var t = new Textbook(id, r.participantId(), r.gtin(), r.title(), r.fullTitle(), r.author(), r.schoolClass(), r.subject(), r.publisher(), r.year(), r.language(), r.isbn(), r.printRun(), r.ageCategory(), r.countryOfProduction(), r.manufacturer(), r.description(), "DRAFT", now, now);
        s.textbooks.put(id, t);
        return t;
    }

    @PostMapping("/{id}/publish")
    Textbook publish(@PathVariable UUID id, @RequestHeader("Authorization") String authorization) {
        var t = s.textbook(id);
        if (!s.owns(s.requestUser(authorization), t.participantId()))
            throw s.error("FORBIDDEN", "Нельзя публиковать чужую карточку", HttpStatus.FORBIDDEN);
        var n = new Textbook(t.id(), t.participantId(), t.gtin(), t.title(), t.fullTitle(), t.author(), t.schoolClass(), t.subject(), t.publisher(), t.year(), t.language(), t.isbn(), t.printRun(), t.ageCategory(), t.countryOfProduction(), t.manufacturer(), t.description(), "PUBLISHED", t.createdAt(), OffsetDateTime.now());
        s.textbooks.put(id, n);
        return n;
    }
}

@RestController
@RequestMapping("/api/marking-codes")
class CodesApi {
    final AisService s;

    CodesApi(AisService s) {
        this.s = s;
    }

    @GetMapping
    List<MarkingCode> all(@RequestHeader("Authorization") String authorization) {
        User u = s.requestUser(authorization);
        return s.codes.values().stream().filter(c -> s.owns(u, c.participantId())).toList();
    }

    @GetMapping("/{id}")
    MarkingCode get(@PathVariable UUID id, @RequestHeader("Authorization") String authorization) {
        var c = s.code(id);
        if (!s.owns(s.requestUser(authorization), c.participantId()))
            throw s.error("FORBIDDEN", "Код принадлежит другому участнику", HttpStatus.FORBIDDEN);
        return c;
    }

    @GetMapping("/lookup")
    Map<String, Object> lookup(@RequestParam String gtin, @RequestParam String serial) {
        var code = s.codes.values().stream().filter(c -> c.gtin().equals(gtin) && c.serial().equals(serial)).findFirst().orElseThrow(() -> s.error("CODE_NOT_FOUND", "Код маркировки не найден", HttpStatus.NOT_FOUND));
        var textbook = s.textbook(code.textbookId());
        var participant = s.participant(code.participantId());
        var codeHistory = s.history.values().stream().filter(h -> h.codeId().equals(code.id())).sorted(Comparator.comparing(History::createdAt)).toList();
        return Map.of("code", code, "textbook", textbook, "participant", participant, "history", codeHistory);
    }

    @PostMapping("/generate")
    List<MarkingCode> generate(@RequestParam UUID participantId, @RequestParam UUID textbookId, @RequestParam @Min(1) int quantity, @RequestHeader("Authorization") String authorization) {
        User u = s.requestUser(authorization);
        if (!s.owns(u, participantId))
            throw s.error("FORBIDDEN", "Оператор может формировать КМ только для своего участника", HttpStatus.FORBIDDEN);
        return s.generate(participantId, textbookId, quantity);
    }

    @PostMapping("/{id}/apply")
    MarkingCode apply(@PathVariable UUID id, @RequestHeader("Authorization") String authorization) {
        User u = s.requestUser(authorization);
        MarkingCode c = s.code(id);
        if (!s.owns(u, c.participantId()))
            throw s.error("FORBIDDEN", "Нельзя наносить код другого участника", HttpStatus.FORBIDDEN);
        if (!c.status().equals("EMITTED"))
            throw s.error("INVALID_STATUS_TRANSITION", "Нанести можно только код со статусом EMITTED", HttpStatus.BAD_REQUEST);
        if (s.codeBilling.containsKey(id)) return s.transition(id, "APPLIED", "MARKING");
        BillingOperation bill = s.reserve(c.participantId(), "MARKING", BigDecimal.ONE);
        try {
            MarkingCode result = s.transition(id, "APPLIED", "MARKING");
            s.capture(bill.id());
            s.codeBilling.put(id, bill.id());
            return result;
        } catch (RuntimeException e) {
            s.release(bill.id());
            throw e;
        }
    }

    @PostMapping("/{id}/circulate")
    MarkingCode circulate(@PathVariable UUID id, @RequestHeader("Authorization") String authorization) {
        User u = s.requestUser(authorization);
        MarkingCode c = s.code(id);
        if (!s.owns(u, c.participantId()))
            throw s.error("FORBIDDEN", "Нельзя вводить в оборот чужой код", HttpStatus.FORBIDDEN);
        return s.transition(id, "IN_CIRCULATION", "INTRODUCTION");
    }

    @PostMapping("/{id}/withdraw")
    MarkingCode withdraw(@PathVariable UUID id, @RequestHeader("Authorization") String authorization) {
        User u = s.requestUser(authorization);
        MarkingCode c = s.code(id);
        if (!s.owns(u, c.participantId()))
            throw s.error("FORBIDDEN", "Нельзя выводить чужой код", HttpStatus.FORBIDDEN);
        return s.transition(id, "WITHDRAWN", "WITHDRAWAL");
    }
}

@RestController
@RequestMapping("/api/code-orders")
class OrdersApi {
    final AisService s;

    OrdersApi(AisService s) {
        this.s = s;
    }

    record Req(UUID participantId, UUID textbookId, @Min(1) int quantity) {
    }

    @GetMapping
    List<CodeOrder> all(@RequestHeader("Authorization") String authorization) {
        User u = s.requestUser(authorization);
        return s.orders.values().stream().filter(o -> s.owns(u, o.participantId())).toList();
    }

    @PostMapping
    CodeOrder create(@Valid @RequestBody Req r, @RequestHeader("Authorization") String authorization) {
        User u = s.requestUser(authorization);
        if (!s.owns(u, r.participantId()))
            throw s.error("FORBIDDEN", "Нельзя создать заказ для другого участника", HttpStatus.FORBIDDEN);
        var t = s.textbook(r.textbookId());
        if (!t.participantId().equals(r.participantId()))
            throw s.error("PARTICIPANT_MISMATCH", "Учебник принадлежит другому участнику", HttpStatus.BAD_REQUEST);
        BigDecimal amount = s.price(r.quantity());
        BillingOperation bill = s.reserve(r.participantId(), "CODE_ORDER", amount);
        UUID id = UUID.randomUUID();
        var now = OffsetDateTime.now();
        try {
            var generated = s.generate(r.participantId(), r.textbookId(), r.quantity());
            for (var code : generated) s.codeBilling.put(code.id(), bill.id());
            s.capture(bill.id());
            var done = new CodeOrder(id, r.participantId(), r.textbookId(), t.gtin(), r.quantity(), amount, bill.id(), "COMPLETED", now);
            s.orders.put(id, done);
            UUID docId = UUID.randomUUID();
            s.documents.put(docId, new Document(docId, s.docNumber("CODE_ORDER"), "CODE_ORDER", r.participantId(), "COMPLETED", OffsetDateTime.now(), null, bill.id()));
            return done;
        } catch (RuntimeException e) {
            s.release(bill.id());
            var failed = new CodeOrder(id, r.participantId(), r.textbookId(), t.gtin(), r.quantity(), amount, bill.id(), "FAILED", now);
            s.orders.put(id, failed);
            throw e;
        }
    }
}

@RestController
@RequestMapping("/api/billing")
class BillingApi {
    final AisService s;

    BillingApi(AisService s) {
        this.s = s;
    }

    record Calc(String operation, int quantity, BigDecimal amount) {
    }

    @GetMapping("/balance/{participantId}")
    Balance balance(@PathVariable UUID participantId, @RequestHeader("Authorization") String authorization) {
        User u = s.requestUser(authorization);
        if (!s.owns(u, participantId)) throw s.error("FORBIDDEN", "Нет доступа к финансовому счёту участника", HttpStatus.FORBIDDEN);
        return s.balance(participantId);
    }

    @GetMapping("/history/{participantId}")
    List<BillingOperation> history(@PathVariable UUID participantId, @RequestHeader("Authorization") String authorization) {
        User u = s.requestUser(authorization);
        if (!s.owns(u, participantId)) throw s.error("FORBIDDEN", "Нет доступа к финансовой истории участника", HttpStatus.FORBIDDEN);
        s.participant(participantId);
        return s.billing.values().stream().filter(x -> x.participantId().equals(participantId))
                .sorted(Comparator.comparing(BillingOperation::createdAt).reversed()).toList();
    }

    @PostMapping("/deposit/{participantId}")
    BillingOperation deposit(@PathVariable UUID participantId, @RequestParam BigDecimal amount, @RequestHeader("Authorization") String authorization) {
        User u = s.requestUser(authorization);
        if (!u.role().equals("ADMIN")) throw s.error("FORBIDDEN", "Пополнять счета может только администратор", HttpStatus.FORBIDDEN);
        s.participant(participantId);
        if (amount == null || amount.signum() <= 0) throw s.error("INVALID_AMOUNT", "Сумма должна быть больше нуля", HttpStatus.BAD_REQUEST);
        UUID id = UUID.randomUUID();
        var now = OffsetDateTime.now();
        s.balances.merge(participantId, amount, BigDecimal::add);
        var op = new BillingOperation(id, participantId, "DEPOSIT", amount, "CAPTURED", now, now);
        s.billing.put(id, op);
        return op;
    }

    @PostMapping("/calculate")
    Calc calculate(@RequestParam String operation, @RequestParam @Min(1) int quantity) {
        return new Calc(operation, quantity, s.price(quantity));
    }

    @PostMapping("/reserve")
    BillingOperation reserve(@RequestParam UUID participantId, @RequestParam String operation, @RequestParam BigDecimal amount) {
        return s.reserve(participantId, operation, amount);
    }

    @PostMapping("/{id}/capture")
    BillingOperation capture(@PathVariable UUID id) {
        return s.capture(id);
    }

    @PostMapping("/{id}/release")
    BillingOperation release(@PathVariable UUID id) {
        return s.release(id);
    }

    @PostMapping("/{id}/refund")
    BillingOperation refund(@PathVariable UUID id) {
        return s.refund(id);
    }
}

@RestController
@RequestMapping("/api/operations")
class OperationsApi {
    final AisService s;

    OperationsApi(AisService s) {
        this.s = s;
    }

    record Req(UUID participantId, UUID textbookId, List<UUID> codeIds, String reason) {
    }

    @GetMapping
    List<Operation> all(@RequestHeader("Authorization") String authorization) {
        User u = s.requestUser(authorization);
        return s.operations.values().stream().filter(o -> s.owns(u, o.participantId())).toList();
    }

    @PostMapping("/marking")
    Operation marking(@Valid @RequestBody Req r, @RequestHeader("Authorization") String authorization) {
        return execute("MARKING", r, "EMITTED", "APPLIED", s.requestUser(authorization));
    }

    @PostMapping("/introduction")
    Operation intro(@Valid @RequestBody Req r, @RequestHeader("Authorization") String authorization) {
        return execute("INTRODUCTION", r, "APPLIED", "IN_CIRCULATION", s.requestUser(authorization));
    }

    @PostMapping("/withdrawal")
    Operation withdrawal(@Valid @RequestBody Req r, @RequestHeader("Authorization") String authorization) {
        return execute("WITHDRAWAL", r, "IN_CIRCULATION", "WITHDRAWN", s.requestUser(authorization));
    }

    private Operation execute(String type, Req r, String from, String to, User u) {
        if (!s.owns(u, r.participantId())) throw s.error("FORBIDDEN", "Нельзя выполнять операцию для другого участника", HttpStatus.FORBIDDEN);
        if (r.codeIds() == null || r.codeIds().isEmpty()) throw s.error("CODES_REQUIRED", "Необходимо указать коды", HttpStatus.BAD_REQUEST);
        for (UUID id : r.codeIds()) {
            var c = s.code(id);
            if (!c.participantId().equals(r.participantId())) throw s.error("PARTICIPANT_MISMATCH", "Код принадлежит другому участнику", HttpStatus.BAD_REQUEST);
            if (!c.status().equals(from)) throw s.error("INVALID_STATUS_TRANSITION", "Недопустимый статус кода", HttpStatus.BAD_REQUEST);
        }
        UUID opId = UUID.randomUUID();
        UUID billingId = null;
        if (type.equals("MARKING")) {
            long chargeable = r.codeIds().stream().filter(id -> !s.codeBilling.containsKey(id)).count();
            if (chargeable > 0) {
                BillingOperation bill = s.reserve(r.participantId(), "MARKING", s.price((int) chargeable));
                billingId = bill.id();
                try {
                    for (UUID id : r.codeIds()) s.transition(id, to, type);
                    s.capture(bill.id());
                    for (UUID id : r.codeIds()) if (!s.codeBilling.containsKey(id)) s.codeBilling.put(id, bill.id());
                } catch (RuntimeException e) {
                    s.release(bill.id());
                    throw e;
                }
            } else if (!r.codeIds().isEmpty()) {
                billingId = s.codeBilling.get(r.codeIds().get(0));
                for (UUID id : r.codeIds()) s.transition(id, to, type);
            }
        } else {
            for (UUID id : r.codeIds()) s.transition(id, to, type);
        }
        UUID docId = UUID.randomUUID();
        s.documents.put(docId, new Document(docId, s.docNumber(type), type, r.participantId(), "COMPLETED", OffsetDateTime.now(), opId, billingId));
        for (UUID id : r.codeIds()) {
            s.history.values().stream().filter(h -> h.codeId().equals(id) && h.documentId() == null)
                    .max(Comparator.comparing(History::createdAt))
                    .ifPresent(h -> s.history.put(h.id(), new History(h.id(), h.codeId(), h.operation(), h.oldStatus(), h.newStatus(), h.participantId(), docId, opId, billingId, h.createdAt())));
        }
        var op = new Operation(opId, type, r.participantId(), r.textbookId(), r.codeIds(), r.codeIds().size(), "SUCCESS", r.reason(), billingId, OffsetDateTime.now());
        s.operations.put(opId, op);
        return op;
    }
}

@RestController
@RequestMapping("/api/aggregations")
class AggregationApi {
    final AisService s;

    AggregationApi(AisService s) {
        this.s = s;
    }

    record Req(UUID participantId, String sscc, List<UUID> codeIds) {
    }

    @GetMapping
    List<Aggregate> all() {
        return new ArrayList<>(s.aggregates.values());
    }

    @PostMapping
    Aggregate create(@Valid @RequestBody Req r) {
        if (r.codeIds() == null || r.codeIds().isEmpty())
            throw s.error("CODES_REQUIRED", "Необходимо указать коды", HttpStatus.BAD_REQUEST);
        for (UUID id : r.codeIds()) {
            var c = s.code(id);
            if (!c.participantId().equals(r.participantId()) || !c.status().equals("IN_CIRCULATION"))
                throw s.error("INVALID_AGGREGATION", "Код нельзя агрегировать", HttpStatus.BAD_REQUEST);
        }
        UUID id = UUID.randomUUID();
        var a = new Aggregate(id, r.sscc(), List.copyOf(r.codeIds()), "ACTIVE", OffsetDateTime.now());
        s.aggregates.put(id, a);
        return a;
    }

    @PostMapping("/{id}/disaggregate")
    Aggregate disaggregate(@PathVariable UUID id) {
        var a = s.aggregates.get(id);
        if (a == null) throw s.error("AGGREGATE_NOT_FOUND", "Агрегация не найдена", HttpStatus.NOT_FOUND);
        var n = new Aggregate(a.id(), a.sscc(), a.codeIds(), "DISAGGREGATED", a.createdAt());
        s.aggregates.put(id, n);
        return n;
    }
}

@RestController
@RequestMapping("/api/documents")
class DocumentsApi {
    final AisService s;

    DocumentsApi(AisService s) {
        this.s = s;
    }

    @GetMapping
    List<Document> all(@RequestHeader("Authorization") String authorization) {
        User u = s.requestUser(authorization);
        return s.documents.values().stream().filter(d -> s.owns(u, d.participantId())).toList();
    }
}

@RestController
@RequestMapping("/api/history")
class HistoryApi {
    final AisService s;

    HistoryApi(AisService s) {
        this.s = s;
    }

    @GetMapping
    List<History> all(@RequestHeader("Authorization") String authorization) {
        User u = s.requestUser(authorization);
        return s.history.values().stream().filter(h -> s.owns(u, h.participantId())).toList();
    }
}

@RestController
@RequestMapping("/api/users")
class UsersApi {
    final AisService s;

    UsersApi(AisService s) {
        this.s = s;
    }

    record Req(@NotBlank String fullName, @NotBlank String login, @NotBlank String password, UUID participantId,
               @NotBlank String role) {
    }

    @GetMapping
    List<User> all() {
        return new ArrayList<>(s.users.values());
    }

    @PostMapping
    User create(@Valid @RequestBody Req r) {
        if (s.users.values().stream().anyMatch(u -> u.login().equals(r.login())))
            throw s.error("LOGIN_ALREADY_EXISTS", "Логин уже используется", HttpStatus.CONFLICT);
        if (!r.role().equals("ADMIN") && !r.role().equals("USER")) throw s.error("INVALID_ROLE", "Допустимые роли: ADMIN или USER", HttpStatus.BAD_REQUEST);
        if (r.role().equals("USER") && r.participantId() == null) throw s.error("PARTICIPANT_REQUIRED", "Пользователь должен быть привязан к участнику", HttpStatus.BAD_REQUEST);
        UUID id = UUID.randomUUID();
        var u = new User(id, r.fullName(), r.login(), r.participantId(), r.role(), "ACTIVE", OffsetDateTime.now());
        s.users.put(id, u);
        s.passwords.put(id, r.password());
        return u;
    }
}

@RestController
@RequestMapping("/api/auth")
class AuthApi {
    final AisService s;

    AuthApi(AisService s) {
        this.s = s;
    }

    record Req(@NotBlank String login, @NotBlank String password) {
    }

    record Res(String token, User user) {
    }

    @PostMapping("/login")
    Res login(@Valid @RequestBody Req r) {
        String token = s.login(r.login(), r.password());
        return new Res(token, s.currentUser(token));
    }

    @GetMapping("/me")
    User me(@RequestHeader("Authorization") String authorization) {
        if (!authorization.startsWith("Bearer "))
            throw s.error("UNAUTHORIZED", "Требуется авторизация", HttpStatus.UNAUTHORIZED);
        return s.currentUser(authorization.substring(7));
    }
}

@RestController
class DashboardApi {
    final AisService s;

    DashboardApi(AisService s) {
        this.s = s;
    }

    @GetMapping("/api/dashboard")
    Map<String, Object> dashboard() {
        return Map.of("participants", s.participants.size(), "textbooks", s.textbooks.size(), "codes", s.codes.size(), "emitted", s.codes.values().stream().filter(c -> c.status().equals("EMITTED")).count(), "applied", s.codes.values().stream().filter(c -> c.status().equals("APPLIED")).count(), "inCirculation", s.codes.values().stream().filter(c -> c.status().equals("IN_CIRCULATION")).count(), "withdrawn", s.codes.values().stream().filter(c -> c.status().equals("WITHDRAWN")).count(), "orders", s.orders.size(), "operations", s.operations.size(), "aggregations", s.aggregates.size());
    }
}
