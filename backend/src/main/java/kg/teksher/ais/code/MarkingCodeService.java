package kg.teksher.ais.code;

import kg.teksher.ais.common.ApiException;
import kg.teksher.ais.textbook.Textbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class MarkingCodeService {
    private final MarkingCodeRepository repo;
    private final SerialGenerator serials;
    private final Gs1Builder gs1;
    private final DataMatrixService matrix;

    public MarkingCodeService(MarkingCodeRepository r, SerialGenerator s, Gs1Builder g, DataMatrixService m) {
        repo = r;
        serials = s;
        gs1 = g;
        matrix = m;
    }

    public List<MarkingCode> all() {
        return repo.all();
    }

    public List<MarkingCode> generate(Textbook t, UUID participantId, int quantity) {
        Set<String> existing = new HashSet<>();
        repo.all().forEach(c -> existing.add(c.serial()));
        List<String> ss = serials.generate(quantity, existing);
        List<MarkingCode> out = new ArrayList<>();
        for (String serial : ss) {
            String raw = gs1.raw(t.gtin(), serial);
            out.add(repo.save(new MarkingCode(UUID.randomUUID(), t.id(), participantId, t.gtin(), serial, raw, matrix.pngBase64(raw), MarkingCode.Status.EMITTED, OffsetDateTime.now())));
        }
        return out;
    }

    public MarkingCode get(UUID id) {
        return repo.findById(id).orElseThrow(() -> new ApiException("CODE_NOT_FOUND", "Код маркировки не найден", HttpStatus.NOT_FOUND));
    }

    public MarkingCode transition(UUID id, MarkingCode.Status target) {
        var c = get(id);
        boolean ok = switch (c.status()) {
            case EMITTED -> target == MarkingCode.Status.APPLIED;
            case APPLIED -> target == MarkingCode.Status.IN_CIRCULATION;
            case IN_CIRCULATION -> target == MarkingCode.Status.WITHDRAWN;
            default -> false;
        };
        if (!ok)
            throw new ApiException("INVALID_STATUS_TRANSITION", "Недопустимый переход статуса кода", HttpStatus.BAD_REQUEST);
        return repo.save(new MarkingCode(c.id(), c.textbookId(), c.participantId(), c.gtin(), c.serial(), c.gs1Payload(), c.dataMatrix(), target, c.createdAt()));
    }
}