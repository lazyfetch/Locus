import json, psycopg2, re
from pathlib import Path

CONFIG_PATH = Path(__file__).parent.parent / "config.json"
with open(CONFIG_PATH) as f:
    cfg = json.load(f)
DB_CONFIG = cfg["DB_CONFIG"]

KIM_DIR = Path(__file__).parent
kim_files = sorted(KIM_DIR.glob("kim*.json"))
if not kim_files:
    print("No kim*.json files found. Exiting.")
    exit(0)

conn = psycopg2.connect(**DB_CONFIG)
cur = conn.cursor()

cur.execute("""
    SELECT scheme_code, scheme_name FROM mf_scheme
    WHERE fund_house = 'SBI MUTUAL FUND'
""")
db_lookup = {}
for sc, sn in cur.fetchall():
   
    key = sn.upper()
    key = re.sub(r' - DIRECT PLAN - (GROWTH|REGULAR)', '', key)
    key = key.replace(" - ", " ").replace("-", " ").replace("  ", " ").strip().rstrip(".")
    db_lookup[key] = sc

def normalise(name):
    n = name.upper()
    n = n.replace(" - ", " ").replace("-", " ").replace("  ", " ").strip().rstrip(".")
    return n

total_ins = 0
unmatched = []

for json_file in kim_files:
    with open(json_file) as f:
        funds = json.load(f)
    for fund in funds:
        name = fund["fund_name"]
        norm = normalise(name)
        code = db_lookup.get(norm)

        if not code:
            for dbn, dbc in db_lookup.items():
                if norm in dbn or dbn in norm:
                    code = dbc
                    break

        if not code:
            unmatched.append(name)
            continue

        cur.execute("DELETE FROM mf_chunks WHERE scheme_code = %s", (code,))

        chunks = []
        if fund.get("investment_objective"):
            chunks.append(("investment_objective", fund["investment_objective"]))
        if fund.get("investment_strategy"):
            chunks.append(("investment_strategy", fund["investment_strategy"]))
        if fund.get("asset_allocation"):
            chunks.append(("asset_allocation", fund["asset_allocation"]))
        if fund.get("Underlying index"):
            chunks.append(("underlying_index", fund["Underlying index"]))

        for idx, (section_type, text) in enumerate(chunks, 1):
            if not text or len(text.strip()) < 10:
                continue
            cur.execute("""
                INSERT INTO mf_chunks (scheme_code, chunk_index, section_type, chunk_text)
                VALUES (%s, %s, %s, %s)
            """, (code, idx, section_type, text.strip()))
            total_ins += 1
    conn.commit()

cur.close()
conn.close()
print(f"Inserted {total_ins} chunks.")
if unmatched:
    print("Unmatched funds:", *unmatched, sep="\n  ")