import requests
import json
import re
import psycopg2
from datetime import datetime, timedelta
import pdfplumber
from io import BytesIO

# 1 Configuration
with open("config.json", "r", encoding="utf-8") as f:
    cfg = json.load(f)

DB_CONFIG = cfg["DB_CONFIG"]
HEADERS = cfg["HEADERS"]
CDN_BASE = cfg["HDFC_CDN_BASE"]

# 2 Manual PDF name overrides
MANUAL_PDF_NAMES = {}
try:
    with open("manual_pdf_names.json", "r", encoding="utf-8") as f:
        MANUAL_PDF_NAMES = json.load(f)
except FileNotFoundError:
    pass

def get_base_name(scheme_name):
    if scheme_name in MANUAL_PDF_NAMES:
        return MANUAL_PDF_NAMES[scheme_name]
    return scheme_name.split(' - ')[0].strip()

# 3 URL existence check 
def url_exists(url, timeout=4):
    try:
        resp = requests.get(url, headers=HEADERS, timeout=timeout, stream=True)
        resp.close()
        return resp.status_code == 200
    except:
        return False

# 4 Find PDF URL

def find_factsheet_url(base_name):
    templates = [
        ("dash-space",   "Fund Facts - {base}_{month} {year}.pdf"),
        ("dash-nospace", "Fund Facts -{base}_{month} {year}.pdf"),
        # underscore date with (1), (2)
        ("dash-space",   "Fund Facts - {base}_{month} {year} (1).pdf"),
        ("dash-nospace", "Fund Facts -{base}_{month} {year} (1).pdf"),
        # underscore date with _0, _1
        ("dash-space",   "Fund Facts - {base}_{month} {year}_0.pdf"),
        ("dash-space",   "Fund Facts - {base}_{month} {year}_1.pdf"),
        ("dash-nospace", "Fund Facts -{base}_{month} {year}_0.pdf"),
        ("dash-nospace", "Fund Facts -{base}_{month} {year}_1.pdf"),

        ("dash-space",   "Fund Facts - {base} - {month} {year}.pdf"),
        ("dash-nospace", "Fund Facts -{base} - {month} {year}.pdf"),
        # dash date with [a]
        ("dash-space",   "Fund Facts - {base} - {month} {year} [a].pdf"),
        ("dash-nospace", "Fund Facts -{base} - {month} {year} [a].pdf"),
    ]

    years = ["%y", "%Y"]

    # Try last 4 months first, then next 8 months
    for depth in [4, 12]:
        month_range = range(depth) if depth == 4 else range(5, 12)
        for months_back in month_range:
            target = datetime.now() - timedelta(days=30 * months_back)
            folder = target.strftime("%Y-%m")
            month_name = target.strftime("%B")

            for dash_style, template in templates:
                for year_fmt in years:
                    year_str = target.strftime(year_fmt)
                    filename = template.format(
                        base=base_name,
                        month=month_name,
                        year=year_str
                    )
                    url = f"{CDN_BASE}/Others/{folder}/{requests.utils.quote(filename)}"
                    if url_exists(url):
                        return url
    return None


# 5 Extract chunks 
def extract_chunks(pdf_bytes, scheme_name):
    chunks = []
    full_text = ""

    with pdfplumber.open(BytesIO(pdf_bytes)) as pdf:
        for page in pdf.pages:
            extracted = page.extract_text()
            if extracted:
                full_text += extracted + "\n"

        m = re.search(r"Investment\s+Objective\s*(.*?)(?=Investment\s+Strategy|AUM|Top\s+\d+|Quantitative|$)", full_text, re.DOTALL | re.IGNORECASE)
        if m:
            chunks.append(("OBJECTIVE", m.group(1).strip()))

        strategy_text = None
        m = re.search(r"(?:Investment\s+Strategy|Investment\s+ Philosophy|Investment\s+Approach)[:\s]*(.*?)(?=Investment\s+Objective|AUM|Top\s+\d+|Quantitative|$)", full_text, re.DOTALL | re.IGNORECASE)
        if m:
            strategy_text = m.group(1).strip()
        else:
            obj_match = re.search(r"Investment\s+Objective\s*(.*?)(?=Investment\s+Strategy|AUM|Top\s+\d+|Quantitative|$)", full_text, re.DOTALL | re.IGNORECASE)
            if obj_match:
                obj_text = obj_match.group(1).strip()
                sentences = re.split(r'(?<=[.!?])\s+', obj_text)
                if len(sentences) >= 2:
                    strategy_text = ' '.join(sentences[1:])
        if strategy_text:
            chunks.append(("STRATEGY", re.sub(r'\s+', ' ', strategy_text)))

        suitability = None
        m = re.search(r"This\s+product\s+is\s+suitable\s+for\s+investors\s+who\s+are\s+seeking.*?\.(?:\s|$)", full_text, re.IGNORECASE)
        if m:
            suitability = m.group(0).strip()
        if not suitability:
            m = re.search(r"This\s+product\s+is\s+suitable\s+for[^.]*\.", full_text, re.IGNORECASE)
            if m:
                suitability = m.group(0).strip()
        if not suitability:
            m = re.search(r"Suitability[:\s]*(.*?)(?=Risk\s+Profile|Risk\s+Factors|Product\s+Label|$)", full_text, re.DOTALL | re.IGNORECASE)
            if m:
                suitability = re.sub(r'\s+', ' ', m.group(1)).strip()
        if suitability:
            chunks.append(("SUITABILITY", re.sub(r'\s+', ' ', suitability)))

        wiwo_categories = {"ENTRY": [], "EXIT": [], "INCREASED EXPOSURE": [], "DECREASED EXPOSURE": []}
        current_category = None
        found_wiwo = False

        for page in pdf.pages:
            tables = page.extract_tables()
            if not tables:
                continue
            for table in tables:
                for row in table:
                    if not row:
                        continue
                    cell_0 = str(row[0]).replace('\n', ' ').strip() if row[0] else ""
                    # Skip empty rows
                    if not cell_0:
                        continue
                    cell_1 = str(row[1]).replace('\n', ' ').strip() if len(row) > 1 and row[1] else "Unknown"
                    lower = cell_0.lower()
                    if lower == "entry":
                        current_category = "ENTRY"
                        found_wiwo = True
                        continue
                    elif lower == "exit":
                        current_category = "EXIT"
                        found_wiwo = True
                        continue
                    elif lower == "increased exposure":
                        current_category = "INCREASED EXPOSURE"
                        found_wiwo = True
                        continue
                    elif lower == "decreased exposure":
                        current_category = "DECREASED EXPOSURE"
                        found_wiwo = True
                        continue
                    elif "exit load" in lower or "category of scheme" in lower or "risk" in lower:
                        continue

                    # If we are inside a WIWO category and have a non-title cell, add the stock
                    if current_category and cell_0 and cell_0.lower() not in ["company name", "nil", ""]:
                        wiwo_categories[current_category].append(f"- {cell_0} ({cell_1})")

            if found_wiwo:
                break   # Stop after first page that has the table

        wiwo_text = "PORTFOLIO CHANGES (What's In / What's Out):\n"
        for cat, items in wiwo_categories.items():
            wiwo_text += f"\n[{cat}]\n"
            if not items:
                wiwo_text += "- None\n"
            for item in items:
                wiwo_text += f"{item}\n"

        if any(wiwo_categories.values()):
            chunks.append(("PORTFOLIO_CHANGES", wiwo_text.strip()))

    return chunks

# 6 Upsert chunks
def upsert_chunks(cur, scheme_code, chunks):
    cur.execute("DELETE FROM mf_chunks WHERE scheme_code = %s AND section_type IN ('OBJECTIVE','STRATEGY','SUITABILITY','PORTFOLIO_CHANGES')", (scheme_code,))
    for idx, (stype, text) in enumerate(chunks):
        cur.execute(
            "INSERT INTO mf_chunks (scheme_code, chunk_index, section_type, chunk_text) VALUES (%s, %s, %s, %s)",
            (scheme_code, idx, stype, text)
        )

def main():
    conn = psycopg2.connect(**DB_CONFIG)
    cur = conn.cursor()
    cur.execute("SELECT scheme_code, scheme_name FROM mf_scheme WHERE fund_house = 'HDFC Mutual Fund' AND is_locus_target = TRUE")
    funds = cur.fetchall()

    success = []
    failed = []
    missing_chunks = []
    EXPECTED = {'OBJECTIVE', 'STRATEGY', 'SUITABILITY', 'PORTFOLIO_CHANGES'}

    for code, name in funds:
        print(f"\n{name}")
        base = get_base_name(name)
        url = find_factsheet_url(base)

        if not url:
            # Debug: show example URL for manual fix
            target = datetime.now()
            folder = target.strftime("%Y-%m")
            month_name = target.strftime("%B")
            yy = target.strftime("%y")
            example_url = f"{CDN_BASE}/Others/{folder}/{requests.utils.quote(f'Fund Facts - {base}_{month_name} {yy}.pdf')}"
            print(f"  -> PDF not found. Example URL: {example_url}")
            failed.append(name)
            continue

        print(f"  -> {url}")
        try:
            resp = requests.get(url, headers=HEADERS, timeout=30)
            resp.raise_for_status()
            chunks = extract_chunks(resp.content, name)
            if chunks:
                upsert_chunks(cur, code, chunks)
                conn.commit()
                chunk_types = {c[0] for c in chunks}
                success.append((name, chunk_types))
                print(f"  -> Stored {len(chunks)} chunks: {chunk_types}")
                missing = EXPECTED - chunk_types
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
        print(f"\n--- FUNDS WITH INCOMPLETE CHUNKS (missing at least one of {EXPECTED}) ---")
        for fname, missing in sorted(missing_chunks, key=lambda x: x[0]):
            print(f"  - {fname}\n    Missing: {sorted(missing)}")

    if success and not missing_chunks and not failed:
        print("\nAll funds processed with complete chunks!")

    print("=" * 70)

if __name__ == "__main__":
    main()