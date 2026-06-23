import json
import psycopg2
from pathlib import Path

CONFIG_PATH = Path(__file__).parent.parent / "config.json"
with open(CONFIG_PATH, "r", encoding="utf-8") as f:
    cfg = json.load(f)

DB_CONFIG = cfg["DB_CONFIG"]

JSON_PATH = Path(__file__).parent / "ppfas_website.json"
with open(JSON_PATH, "r", encoding="utf-8") as f:
    website_data = json.load(f)   
print(f"Loaded data for {len(website_data)} schemes.")


def upsert_website_chunks(cur, scheme_code, chunks):
    section_types = [chunk['section_type'] for chunk in chunks]
    cur.execute(
        "DELETE FROM mf_chunks WHERE scheme_code = %s AND section_type IN %s",
        (scheme_code, tuple(section_types))
    )

    cur.execute(
        "SELECT COALESCE(MAX(chunk_index), -1) FROM mf_chunks WHERE scheme_code = %s",
        (scheme_code,)
    )
    max_idx = cur.fetchone()[0]

    for i, chunk in enumerate(chunks, start=max_idx + 1):
        cur.execute(
            "INSERT INTO mf_chunks (scheme_code, chunk_index, section_type, chunk_text) VALUES (%s, %s, %s, %s)",
            (scheme_code, i, chunk['section_type'], chunk['chunk_text'])
        )

conn = psycopg2.connect(**DB_CONFIG)
cur = conn.cursor()

inserted = 0
for code_str, chunks in website_data.items():
    scheme_code = int(code_str)   # keys are strings like "122639"
    print(f"Processing scheme_code {scheme_code} ({len(chunks)} chunks)")
    upsert_website_chunks(cur, scheme_code, chunks)
    inserted += len(chunks)

conn.commit()
cur.close()
conn.close()

print(f"\nDone. Total chunks inserted/updated: {inserted}")