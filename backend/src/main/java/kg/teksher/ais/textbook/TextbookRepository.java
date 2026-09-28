package kg.teksher.ais.textbook;
import java.util.*;
public interface TextbookRepository{Textbook save(Textbook t);Optional<Textbook> findById(UUID id);List<Textbook> findAll();boolean existsByGtin(String gtin);}