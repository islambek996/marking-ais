package kg.teksher.ais.textbook;

public final class GtinValidator {
    private GtinValidator() {
    }

    public static boolean valid(String gtin) {
        if (gtin == null || !gtin.matches("\\d{8}|\\d{12}|\\d{13}|\\d{14}")) return false;
        int sum = 0, pos = 0;
        for (int i = gtin.length() - 2; i >= 0; i--, pos++) {
            int n = gtin.charAt(i) - '0';
            sum += n * (pos % 2 == 0 ? 3 : 1);
        }
        return (10 - sum % 10) % 10 == gtin.charAt(gtin.length() - 1) - '0';
    }
}