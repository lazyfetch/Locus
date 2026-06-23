import requests
import json
import re
import psycopg2
from datetime import datetime
import pdfplumber
from io import BytesIO
from pathlib import Path

CONFIG_PATH = Path(__file__).parent.parent / "config.json"
with open(CONFIG_PATH, "r", encoding="utf-8") as f:
    cfg = json.load(f)

DB_CONFIG = cfg["DB_CONFIG"]
HEADERS = cfg["HEADERS"]

KIM_URLS = {}
url_file = Path(__file__).parent / "manual_kim_urls_ppfas.json"
if url_file.exists():
    with open(url_file, "r", encoding="utf-8") as f:
        KIM_URLS = json.load(f)
    print(f"Loaded {len(KIM_URLS)} KIM URLs from {url_file.name}")
else:
    print("WARNING: No manual_kim_urls_ppfas.json found – cannot fetch KIM PDFs.")

def extract_ppfas_kim_chunks(pdf_bytes):
    """
    Extracts exactly 4 chunks from a PPFAS KIM PDF:
    OBJECTIVE, ASSET_ALLOCATION, STRATEGY, RISK_GENERAL
    (Skips the first SUITABILITY chunk because it's poorly parsed.)
    """
    chunks = []
    full_text = ""

    
    try:
        with pdfplumber.open(BytesIO(pdf_bytes)) as pdf:
            for page in pdf.pages:
                text = page.extract_text()
                if text:
                    full_text += text + "\n"
    except Exception as e:
        print(f"    PDF read error: {e}")
        return []

    clean_text = re.sub(r'\|\s*', '', full_text)
    flat_text = re.sub(r'\s+', ' ', clean_text).strip()

    obj_match = re.search(
        r"Investment Objective\s*(.*?)(?=Asset Allocation pattern)",
        flat_text, re.IGNORECASE
    )
    if obj_match:
        objective = obj_match.group(1).strip()
        chunks.append(("OBJECTIVE", "Investment Objective: " + objective))

    alloc_match = re.search(
        r"Asset Allocation pattern\s*(.*?)(?=Investment Strategy)",
        flat_text, re.IGNORECASE
    )
    if alloc_match:
        alloc_text = alloc_match.group(1).strip()
        chunks.append(("ASSET_ALLOCATION", "Asset Allocation pattern: " + alloc_text))

    strat_match = re.search(
        r"Investment Strategy\s*(.*?)(?=Standard Risk Factors|Risk Profile of the Scheme)",
        flat_text, re.IGNORECASE
    )
    if strat_match:
        strategy_text = strat_match.group(1).strip()
        chunks.append(("STRATEGY", "Investment Strategy: " + strategy_text))

    risk_match = re.search(
        r"Standard Risk Factors of this Scheme are stated below:\s*(.*?)(?=Plans/Options)",
        flat_text, re.IGNORECASE
    )
    if risk_match:
        risk_text = risk_match.group(1).strip()
        chunks.append(("RISK_GENERAL", "Standard Risk Factors: " + risk_text))

    return chunks

def upsert_kim_chunks(cur, scheme_code, chunks):
    """Remove existing PPFAS KIM chunks for this scheme and insert new ones."""
    if not chunks:
        return

    kim_sections = [c[0] for c in chunks]
    cur.execute(
        "DELETE FROM mf_chunks WHERE scheme_code = %s AND section_type IN %s",
        (scheme_code, tuple(kim_sections))
    )

    cur.execute(
        "SELECT COALESCE(MAX(chunk_index), -1) FROM mf_chunks WHERE scheme_code = %s",
        (scheme_code,)
    )
    max_idx = cur.fetchone()[0]

    for i, (stype, text) in enumerate(chunks, start=max_idx + 1):
        cur.execute(
            "INSERT INTO mf_chunks (scheme_code, chunk_index, section_type, chunk_text) VALUES (%s, %s, %s, %s)",
            (scheme_code, i, stype, text)
        )

def main():
    if not KIM_URLS:
        print("No KIM URLs loaded. Exiting.")
        return

    conn = psycopg2.connect(**DB_CONFIG)
    cur = conn.cursor()
    cur.execute("""
        SELECT scheme_code, scheme_name
        FROM mf_scheme
        WHERE fund_house = 'PARAG PARIKH FINANCIAL ADVISORY SERVICES LIMITED'
          AND is_locus_target = TRUE
    """)
    schemes = cur.fetchall()
    print(f"Found {len(schemes)} PPFAS target schemes.\n")

    success = []
    failed = []
    missing_chunks = []
    MINIMUM_CHUNKS = {'OBJECTIVE', 'ASSET_ALLOCATION', 'STRATEGY', 'RISK_GENERAL'}

    for code, name in schemes:
        print(f"Processing: {name}")
        url = KIM_URLS.get(name)
        if not url:
            print(f"  -> No KIM URL configured for this scheme (add to manual_kim_urls_ppfas.json)")
            failed.append(name)
            continue

        print(f"  -> {url}")
        try:
            resp = requests.get(url, headers=HEADERS, timeout=30)
            resp.raise_for_status()
            chunks = extract_ppfas_kim_chunks(resp.content)
            if chunks:
                upsert_kim_chunks(cur, code, chunks)
                conn.commit()
                chunk_types = {c[0] for c in chunks}
                success.append((name, chunk_types))
                print(f"  -> Stored {len(chunks)} chunks: {sorted(chunk_types)}")
                missing = MINIMUM_CHUNKS - chunk_types
                if missing:
                    missing_chunks.append((name, missing))
            else:
                failed.append(name)
                print("  -> No chunks extracted (possibly corrupted PDF or format change)")
        except Exception as e:
            print(f"  -> Error: {e}")
            failed.append(name)

    cur.close()
    conn.close()

    print("\n" + "=" * 70)
    print(f"TOTAL FUNDS: {len(schemes)}")
    print(f"SUCCESS:     {len(success)}")
    print(f"FAILED:      {len(failed)}")

    if failed:
        print("\n--- FAILED (no URL or extraction error) ---")
        for f in failed:
            print(f"  - {f}")

    if missing_chunks:
        print(f"\n--- FUNDS WITH INCOMPLETE CHUNKS (missing at least one of {MINIMUM_CHUNKS}) ---")
        for fname, missing in sorted(missing_chunks, key=lambda x: x[0]):
            print(f"  - {fname}\n    Missing: {sorted(missing)}")

    if success and not failed and not missing_chunks:
        print("\nAll funds processed with complete KIM chunks!")

    print("=" * 70)

if __name__ == "__main__":
    main()