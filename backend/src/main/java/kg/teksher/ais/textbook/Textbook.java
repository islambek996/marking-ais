package kg.teksher.ais.textbook;

import java.time.LocalDate;
import java.util.UUID;

public record Textbook(UUID id, UUID participantId, String gtin, String title, String fullTitle, String author,
                       String schoolClass, String subject, String publisher, LocalDate publicationDate, String language,
                       String isbn, Integer printRun, String ageCategory, String countryOfProduction,
                       String manufacturer, String description, Status status, LocalDate createdAt) {
    public enum Status {DRAFT, PUBLISHED, BLOCKED, ARCHIVED}
}