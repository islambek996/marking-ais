package kg.teksher.ais.product;

import java.util.*;

public record ProductGroup(UUID id, String code, String name, String description, Status status,
                           List<String> requiredCharacteristics) {
    public enum Status {ACTIVE, INACTIVE}
}