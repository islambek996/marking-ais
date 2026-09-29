package kg.teksher.ais.code;

import org.springframework.stereotype.Component;

@Component
public class Gs1Builder {
    public String build(String gtin, String serial) {
        return "(01)" + gtin + "(21)" + serial;
    }

    public String raw(String gtin, String serial) {
        return "01" + gtin + "21" + serial;
    }
}