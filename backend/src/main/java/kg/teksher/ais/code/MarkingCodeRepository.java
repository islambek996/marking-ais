package kg.teksher.ais.code;

import java.util.*;

public interface MarkingCodeRepository {
    MarkingCode save(MarkingCode c);

    List<MarkingCode> all();

    Optional<MarkingCode> findById(UUID id);

    boolean exists(String gtin, String serial);
}