package kg.teksher.ais.participant;
import java.util.*;
public interface ParticipantRepository{Participant save(Participant p);Optional<Participant> findById(UUID id);List<Participant> findAll();}