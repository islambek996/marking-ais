package kg.teksher.ais.participant;

import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryParticipantRepository implements ParticipantRepository {
    private final Map<UUID, Participant> data = new ConcurrentHashMap<>();

    public Participant save(Participant p) {
        data.put(p.id(), p);
        return p;
    }

    public Optional<Participant> findById(UUID id) {
        return Optional.ofNullable(data.get(id));
    }

    public List<Participant> findAll() {
        return new ArrayList<>(data.values());
    }
}