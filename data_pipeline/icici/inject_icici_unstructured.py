import json, psycopg2, re
from pathlib import Path

CONFIG_PATH = Path(__file__).parent.parent / "config.json"
with open(CONFIG_PATH) as f:
    cfg = json.load(f)
DB_CONFIG = cfg["DB_CONFIG"]


JSON_PATH = Path(__file__).parent / "icici_unstructured.json"
with open(JSON_PATH, "r", encoding="utf-8") as f:
    funds = json.load(f)
print(f"Loaded {len(funds)} funds from icici_unstructured.json\n")


conn = psycopg2.connect(**DB_CONFIG)
cur = conn.cursor()


cur.execute("""
    SELECT scheme_code, scheme_name
    FROM mf_scheme
    WHERE fund_house = 'ICICI PRUDENTIAL MUTUAL FUND'
""")
db_lookup = {}
for sc, sn in cur.fetchall():
    key = sn.upper()
    # Remove common suffix
    key = re.sub(r' - DIRECT PLAN - (GROWTH|REGULAR)', '', key)
    # Normalise: dashes to spaces, ampersand to 'and', strip
    key = key.replace(" - ", " ").replace("-", " ")
    key = key.replace("&", "AND")
    key = re.sub(r'\s+', ' ', key).strip().rstrip(".")
    db_lookup[key] = sc

def normalise(name):
    n = name.upper()
    # Override for the Nifty Next 50 entry
    if "NIFTY NEXT 50 INDEX" in n:
        n = "ICICI PRUDENTIAL NIFTY NEXT 50 INDEX FUND"
    n = re.sub(r'\(.*?\)', '', n)
    n = n.replace(" - ", " ").replace("-", " ")
    n = n.replace("&", "AND")
    n = re.sub(r'\s+', ' ', n).strip().rstrip(".")
    return n


inserted = 0
unmatched = []

for fund in funds:
    raw_name = fund["fund_name"]
    norm = normalise(raw_name)

    
    code = db_lookup.get(norm)

    if not code:
        for dbn, dbc in db_lookup.items():
            if norm in dbn or dbn in norm:
                code = dbc
                break

    if not code:
        unmatched.append(raw_name)
        continue

    cur.execute("DELETE FROM mf_chunks WHERE scheme_code = %s", (code,))

    chunk_index = 0
    for field_name, field_value in fund.items():
        if field_name == "fund_name" or not field_value:
            continue
        section_type = field_name[:50]
        chunk_text = field_value.strip()
        if len(chunk_text) < 20:
            continue

        chunk_index += 1
        cur.execute(
            """
            INSERT INTO mf_chunks (scheme_code, chunk_index, section_type, chunk_text)
            VALUES (%s, %s, %s, %s)
            """,
            (code, chunk_index, section_type, chunk_text)
        )
        inserted += 1

    conn.commit()
    print(f"  ✓ {raw_name} → {chunk_index} chunks")

cur.close()
conn.close()

print("\n" + "=" * 60)
print(f"Total chunks inserted: {inserted}")
print(f"Unmatched funds: {len(unmatched)}")
if unmatched:
    for u in unmatched:
        print(f"  - {u}")