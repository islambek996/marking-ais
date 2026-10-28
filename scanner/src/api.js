const API_BASE = (import.meta.env.VITE_API_BASE || "").replace(/\/$/, "");

export async function lookupCode(gtin, serial) {
    if (!API_BASE) {
        throw new Error("API не настроен. Укажите VITE_API_BASE для адреса AIS API.");
    }

    const url = new URL(API_BASE + "/api/marking-codes/lookup");
    url.searchParams.set("gtin", gtin);
    url.searchParams.set("serial", serial);

    const response = await fetch(url);
    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
        const error = new Error(data.message || "Код не найден");
        error.status = response.status;
        error.data = data;
        throw error;
    }

    return data;
}
