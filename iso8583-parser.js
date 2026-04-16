/**
 * ISO8583 BASE24 Parser
 * BCA Digital – Inquiry / Transfer
 * Author: Practical H2H Debug Version
 */

const STRICT_MODE = false; // true = fail fast | false = tolerate PDF sample

/**
 * Normalize ISO string (from PDF / HTML / text)
 */
function normalizeISO(input) {
  return input
    .replace(/\r/g, "")
    .replace(/\n/g, "")
    .replace(/\s+/g, "")
    .replace(/&amp;/g, "&");
}

/**
 * Main ISO8583 parser
 */
function parseISO8583(raw) {
  raw = normalizeISO(raw);

  let idx = 0;

  // ===== BASE24 HEADER =====
  const header = raw.substr(0, 12); // ISO + 9 digit length
  idx += 12;

  // ===== MTI =====
  const mti = raw.substr(idx, 4);
  idx += 4;

  // ===== PRIMARY BITMAP =====
  const primaryBitmapHex = raw.substr(idx, 16);
  idx += 16;

  let bitmapHex = primaryBitmapHex;
  let bitmapBin = hexToBin(primaryBitmapHex);

  // ===== SECONDARY BITMAP =====
  if (bitmapBin[0] === "1") {
    const secondaryBitmapHex = raw.substr(idx, 16);
    idx += 16;

    bitmapHex += secondaryBitmapHex;
    bitmapBin += hexToBin(secondaryBitmapHex);
  }

  // ===== FIELD SPEC (BCAD) =====
  const FIELD_SPEC = {
    2:   { type: "llvar",  desc: "Primary Account Number" },
    3:   { type: "fixed",  len: 6,   desc: "Processing Code" },
    4:   { type: "fixed",  len: 12,  desc: "Amount Transaction" },
    7:   { type: "fixed",  len: 10,  desc: "Transmission Date/Time" },
    11:  { type: "fixed",  len: 6,   desc: "STAN" },
    12:  { type: "fixed",  len: 6,   desc: "Local Transaction Time" },
    13:  { type: "fixed",  len: 4,   desc: "Local Transaction Date" },
    15:  { type: "fixed",  len: 4,   desc: "Settlement Date" },
    17:  { type: "fixed",  len: 4,   desc: "Capture Date" },
    18:  { type: "fixed",  len: 4,   desc: "Merchant Type" },
    22:  { type: "fixed",  len: 3,   desc: "POS Entry Mode" },
    32:  { type: "llvar",  desc: "Acquiring Institution ID" },
    35:  { type: "llvar",  desc: "Track 2 Data" },
    37:  { type: "fixed",  len: 12,  desc: "RRN" },
    38:  { type: "fixed",  len: 6,   desc: "Authorization ID" },
    39:  { type: "fixed",  len: 2,   desc: "Response Code" },
    41:  { type: "fixed",  len: 8,   desc: "Terminal ID" },
    42:  { type: "fixed",  len: 15,  desc: "Merchant ID" },
    43:  { type: "fixed",  len: 40,  desc: "Card Acceptor Name/Location" },
    48:  { type: "lllvar", desc: "Additional Data Private" },
    49:  { type: "fixed",  len: 3,   desc: "Currency Code Transaction" },
    52:  { type: "binary", len: 8,   desc: "PIN Data" },
    60:  { type: "lllvar", desc: "Reserved Private" },
    70:  { type: "fixed",  len: 3,   desc: "Network Management Info Code" },
    102: { type: "llvar",  desc: "Account ID 1 (From Account)" },
    103: { type: "llvar",  desc: "Account ID 2 (To Account)" },
    123: { type: "lllvar", desc: "Reserved Private" },
    126: { type: "lllvar", desc: "Token R1 (BCAD)" }
  };

  // ===== PARSE FIELDS =====
  const fields = {};
  const maxBit = bitmapBin.length;

  for (let bit = 1; bit <= maxBit; bit++) {
    if (bitmapBin[bit - 1] !== "1") continue;
    const spec = FIELD_SPEC[bit];
    if (!spec) continue;

    // ----- FIXED -----
    if (spec.type === "fixed") {
      fields[bit] = raw.substr(idx, spec.len);
      idx += spec.len;
    }

    // ----- LLVAR -----
    if (spec.type === "llvar") {
      const lenStr = raw.substr(idx, 2);

      // ✅ graceful handling for broken PDF samples
      if (!lenStr || isNaN(parseInt(lenStr, 10))) {
        if (STRICT_MODE) {
          throw new Error(`Invalid LLVAR length at bit ${bit}, index ${idx}`);
        }
        continue;
      }

      const len = parseInt(lenStr, 10);
      idx += 2;
      fields[bit] = raw.substr(idx, len);
      idx += len;
    }

    // ----- LLLVAR -----
    if (spec.type === "lllvar") {
      const lenStr = raw.substr(idx, 3);

      if (!lenStr || isNaN(parseInt(lenStr, 10))) {
        if (STRICT_MODE) {
          throw new Error(`Invalid LLLVAR length at bit ${bit}, index ${idx}`);
        }
        continue;
      }

      const len = parseInt(lenStr, 10);
      idx += 3;
      fields[bit] = raw.substr(idx, len);
      idx += len;
    }

    // ----- BINARY -----
    if (spec.type === "binary") {
      // Binary in hex wire is 2 chars per byte
      const len = spec.len * 2;
      fields[bit] = raw.substr(idx, len);
      idx += len;
    }
  }

  return {
    header,
    mti,
    bitmap: bitmapHex,
    fields: Object.keys(fields).reduce((acc, bit) => {
      acc[`DE${bit.padStart(3, '0')}`] = {
        name: FIELD_SPEC[bit].desc,
        value: fields[bit]
      };
      return acc;
    }, {})
  };
}

/**
 * Hex to Binary helper
 */
function hexToBin(hex) {
  return hex
    .split("")
    .map(h => parseInt(h, 16).toString(2).padStart(4, "0"))
    .join("");
}

// ==================================================
// =================== TEST RUN =====================
// ==================================================

const isoSample = `ISO0050000600800822000000000000004000000000000200218073507000050001008BCAD0001`;

try {
  const result = parseISO8583(isoSample);
  console.log("MTI:", result.mti);
  console.log("Fields:");
  Object.keys(result.fields).forEach(id => {
    console.log(`${id} [${result.fields[id].name}]: ${result.fields[id].value}`);
  });
} catch (err) {
  console.error("ISO PARSE ERROR:", err.message);
}