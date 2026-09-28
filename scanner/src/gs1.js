const GROUP_SEPARATOR = String.fromCharCode(29);

export function normalizeRaw(value = "") {
  return value
    .replace(/\\x1D/g, GROUP_SEPARATOR)
    .replace(/<GS>/gi, GROUP_SEPARATOR)
    .trim();
}

export function parseGs1(value = "") {
  const raw = normalizeRaw(value);
  const clean = raw.replace(/^(]d2|]Q3)/, "");

  const result = {
    raw,
    ai: {},
    gtin: "",
    serial: "",
    valid: false
  };

  if (clean.startsWith("01") && clean.length >= 16) {
    result.gtin = clean.slice(2, 16);
    result.ai["01"] = result.gtin;

    let rest = clean.slice(16);
    if (rest.startsWith(GROUP_SEPARATOR)) rest = rest.slice(1);

    if (rest.startsWith("21")) {
      result.serial = rest.slice(2).split(GROUP_SEPARATOR)[0];
      result.ai["21"] = result.serial;
    }
  }

  result.valid = /^\\d{14}$/.test(result.gtin) && result.serial.length > 0;
  return result;
}
