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
    2: { type: "llvar" },
    3: { type: "fixed", len: 6 },
    4: { type: "fixed", len: 12 },
    7: { type: "fixed", len: 10 },
    11: { type: "fixed", len: 6 },
    12: { type: "fixed", len: 6 },
    13: { type: "fixed", len: 4 },
    17: { type: "fixed", len: 4 },
    32: { type: "llvar" },
    35: { type: "llvar" },
    37: { type: "fixed", len: 12 },
    41: { type: "fixed", len: 16 },
    43: { type: "fixed", len: 40 },
    48: { type: "lllvar" },
    49: { type: "fixed", len: 3 },
    60: { type: "lllvar" },
    102: { type: "llvar" },
    103: { type: "llvar" },
    126: { type: "lllvar" }
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
  }

  return {
    header,
    mti,
    bitmap: bitmapHex,
    fields
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

const isoSample = `
ISO0150000100200F238800128A1901000000000020000041650291234567812344010000
0003500000002090728565770021428560209020903536215029123456781234=99995770
02000000100010MOBILE/INTERNETBANKING044A
40000036000000000001360015MOBILE/INTERNET
106290435801292&0000200292!R100270014TESTH2HBCAD
NAMAPENGIRIMINTERBANKTRANSFERTRANSACTION30501
`;

try {
  const result = parseISO8583(isoSample);
  console.log(JSON.stringify(result, null, 2));
} catch (err) {
  console.error("ISO PARSE ERROR:", err.message);
}