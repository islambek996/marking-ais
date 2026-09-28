const API_BASE =
  import.meta.env.VITE_API_BASE ||
  "http://" + window.location.hostname + ":8080";

export async function lookupCode(gtin, serial) {
  const url = new URL(API_BASE + "/api/public/marking-codes/lookup");
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
