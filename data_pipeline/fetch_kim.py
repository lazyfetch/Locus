import requests
import json
import re
import psycopg2
from datetime import datetime, timedelta
from pypdf import PdfReader
from io import BytesIO

# 1 Configuration
with open("config.json", "r", encoding="utf-8") as f:
    cfg = json.load(f)

DB_CONFIG = cfg["DB_CONFIG"]
HEADERS = cfg["HEADERS"]
CDN_BASE = cfg["HDFC_CDN_BASE"]

# 2 Manual KIM name overrides

MANUAL_KIM_NAMES = {}
try:
    with open("manual_kim_names.json", "r", encoding="utf-8") as f:
        MANUAL_KIM_NAMES = json.load(f)
except FileNotFoundError:
    pass

def get_base_name(scheme_name):
    if scheme_name in MANUAL_KIM_NAMES:
        return MANUAL_KIM_NAMES[scheme_name]
    return scheme_name.split(' - ')[0].strip()


# 3 URL existence check

def url_exists(url):
    try:
        resp = requests.get(url, headers=HEADERS, timeout=8, stream=True)
        resp.close()
        return resp.status_code == 200
    except:
        return False

# 4 Find URL 

def find_kim_url(base_name):
    for months_back in range(12):
        target = datetime.now() - timedelta(days=30 * months_back)
        folder = target.strftime("%Y-%m")
        month_name = target.strftime("%B")
        year = target.year
        for day in [21, 1, 15, 10, 25, 5]:
            day_str = f"{day:02d}"
            # Try all three suffix variants: _0, _1, none
            for suffix in ["_1", "_0", ""]:
                filename = f"KIM - {base_name} dated {month_name} {day_str}, {year}{suffix}.pdf"
                url = f"{CDN_BASE}/KIM/{folder}/{requests.utils.quote(filename)}"
                if url_exists(url):
                    return url
    return None


# 5 Extract chunks

def extract_kim_chunks(pdf_bytes):
    reader = PdfReader(BytesIO(pdf_bytes))
    full_text = "\n".join(page.extract_text() or "" for page in reader.pages)

    # Remove page footers like "6 \n HDFC Flexi Cap Fund - KIM"
    full_text = re.sub(r'\b\d+\b\s*\n*\s*HDFC[^\n]+KIM', ' ', full_text, flags=re.IGNORECASE)
    full_text = re.sub(r'\n{2,}', '\n', full_text)

    chunks = []

    
    m = re.search(r"(Investment\s+Strategy.*?)(?=Risk\s+Profile|Product\s+Label|Risk\s+Factors|$)", full_text, re.DOTALL | re.IGNORECASE)
    if m:
        clean = re.sub(r'\s+', ' ', m.group(1)).strip()
        chunks.append(("STRATEGY_KIM", clean))

    risk_pattern = re.compile(
        r'(\([a-z]+\)\s*Risk\s+factors?\s+associated\s+with\s+investing\s+in\s+[^\n]*?)(?=\([a-z]+\)\s*Risk\s+|General\s+Risk|Annexure|Product\s+Label|$)',
        re.DOTALL | re.IGNORECASE
    )
    for m in re.finditer(risk_pattern, full_text):
        risk_title = m.group(1).strip()
        risk_text = re.sub(r'\s+', ' ', risk_title)
        # Extract the ID from the title, e.g., (i) -> i
        id_match = re.match(r'\(([a-z]+)\)', risk_title)
        if id_match:
            risk_id = id_match.group(1).upper()
            chunks.append((f"RISK_{risk_id}", risk_text))

    
    m = re.search(r"(\(xvii\)\s*General\s+Risk\s+factors.*?)(?=\(|Annexure|Product\s+Label|$)", full_text, re.DOTALL | re.IGNORECASE)
    if m:
        clean = re.sub(r'\s+', ' ', m.group(1)).strip()
        chunks.append(("RISK_GENERAL", clean))

    m = re.search(r"(This\s+product\s+is\s+suitable[\s\S]*?)(?=This Key Information|Name of Scheme|Investment Objective)", full_text, re.IGNORECASE)
    if m:
        clean = re.sub(r'\s+', ' ', m.group(1)).strip()
        chunks.append(("SUITABILITY_KIM", clean))

    return chunks


# 6 Insert chunks into DB

def upsert_kim_chunks(cur, scheme_code, chunks):
    # Remove only previous KIM chunks for this scheme
    kim_sections = [c[0] for c in chunks]
    if kim_sections:
        cur.execute(
            "DELETE FROM mf_chunks WHERE scheme_code = %s AND section_type IN %s",
            (scheme_code, tuple(kim_sections))
        )
    # Find next chunk_index
    cur.execute("SELECT COALESCE(MAX(chunk_index), -1) FROM mf_chunks WHERE scheme_code = %s", (scheme_code,))
    max_idx = cur.fetchone()[0]
    for i, (stype, text) in enumerate(chunks, start=max_idx + 1):
        cur.execute(
            "INSERT INTO mf_chunks (scheme_code, chunk_index, section_type, chunk_text) VALUES (%s, %s, %s, %s)",
            (scheme_code, i, stype, text)
        )

def main():
    conn = psycopg2.connect(**DB_CONFIG)
    cur = conn.cursor()
    cur.execute("SELECT scheme_code, scheme_name FROM mf_scheme WHERE fund_house = 'HDFC Mutual Fund' AND is_locus_target = TRUE")
    funds = cur.fetchall()

    success = []
    failed = []
    missing_chunks = []
    MINIMUM_CHUNKS = {'STRATEGY_KIM', 'SUITABILITY_KIM'}

    for code, name in funds:
        print(f"\n{name}")
        base = get_base_name(name)
        url = find_kim_url(base)
        if not url:
            target = datetime.now()
            folder = target.strftime("%Y-%m")
            month_name = target.strftime("%B")
            day_str = "21"
            year = target.year
            example_url = f"{CDN_BASE}/KIM/{folder}/{requests.utils.quote(f'KIM - {base} dated {month_name} {day_str}, {year}_1.pdf')}"
            print(f"  -> Tried base name: '{base}', example URL: {example_url}")
            failed.append(name)
            continue

        print(f"  -> {url}")
        try:
            resp = requests.get(url, headers=HEADERS, timeout=30)
            resp.raise_for_status()
            chunks = extract_kim_chunks(resp.content)
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
                print("  -> No chunks extracted")
        except Exception as e:
            print(f"  -> Error: {e}")
            failed.append(name)

    cur.close()
    conn.close()

    
    print("\n" + "=" * 70)
    print(f"TOTAL FUNDS: {len(funds)}")
    print(f"SUCCESS:     {len(success)}")
    print(f"FAILED:      {len(failed)}")

    if failed:
        print("\n--- FAILED (no PDF or extraction error) ---")
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