import requests
import json
import psycopg2
from datetime import date, timedelta, datetime
import time
from pathlib import Path

CONFIG_PATH = Path(__file__).parent.parent / "config.json"
with open(CONFIG_PATH, "r", encoding="utf-8") as f:
    cfg = json.load(f)

DB_CONFIG = cfg["DB_CONFIG"]
HEADERS = cfg["HEADERS"]
MFAPI_BASE = cfg["MFAPI_BASE_URL"]

def update_metadata(cur, scheme_code, meta):
    """Update mf_scheme with metadata from API if columns are empty."""
    cur.execute(
        "SELECT scheme_type, scheme_category, isin_growth FROM mf_scheme WHERE scheme_code = %s",
        (scheme_code,)
    )
    row = cur.fetchone()
    if not row:
        return
    current_type, current_cat, current_isin = row

    updates = []
    params = []
    if not current_type and meta.get("scheme_type"):
        updates.append("scheme_type = %s")
        params.append(meta["scheme_type"])
    if not current_cat and meta.get("scheme_category"):
        updates.append("scheme_category = %s")
        params.append(meta["scheme_category"])
    if not current_isin and meta.get("isin_growth"):
        updates.append("isin_growth = %s")
        params.append(meta["isin_growth"])

    if updates:
        params.append(scheme_code)
        cur.execute(f"UPDATE mf_scheme SET {', '.join(updates)} WHERE scheme_code = %s", params)

def fetch_nav_history(scheme_code):
    """Fetch NAV history from mfapi.in starting from the last stored date."""
    conn = psycopg2.connect(**DB_CONFIG)
    cur = conn.cursor()
    cur.execute("SELECT MAX(nav_date) FROM mf_nav_history WHERE scheme_code = %s", (scheme_code,))
    last_date = cur.fetchone()[0]
    start_date = last_date + timedelta(days=1) if last_date else date(1990, 1, 1)
    start_str = start_date.strftime("%Y-%m-%d")

    url = f"{MFAPI_BASE}/mf/{scheme_code}?startDate={start_str}"
    print(f"  Fetching NAVs from {start_str} ...")
    try:
        resp = requests.get(url, headers=HEADERS, timeout=30)
        resp.raise_for_status()
        data = resp.json()
        nav_list = data.get("data", [])
        if not nav_list:
            print("    No new NAVs found.")
            cur.close()
            conn.close()
            return 0

        inserted = 0
        for entry in nav_list:
            nav_date = datetime.strptime(entry["date"], "%d-%m-%Y").date()
            nav_val = entry["nav"]
            cur.execute("""
                INSERT INTO mf_nav_history (scheme_code, nav_date, nav)
                VALUES (%s, %s, %s)
                ON CONFLICT (scheme_code, nav_date) DO NOTHING
            """, (scheme_code, nav_date, nav_val))
            if cur.rowcount:
                inserted += 1
        conn.commit()
        cur.close()
        conn.close()
        return inserted
    except Exception as e:
        print(f"    Error fetching NAVs: {e}")
        cur.close()
        conn.close()
        return 0

def main():
    conn = psycopg2.connect(**DB_CONFIG)
    cur = conn.cursor()

    cur.execute("""
        SELECT scheme_code, scheme_name
        FROM mf_scheme
        WHERE fund_house = 'NIPPON INDIA MUTUAL FUND'
          AND is_locus_target = TRUE
        ORDER BY scheme_code
    """)
    schemes = cur.fetchall()
    print(f"Found {len(schemes)} Nippon India target schemes.\n")

    total_navs = 0
    first_print_done = False

    for idx, (code, name) in enumerate(schemes):
        print(f"[{idx+1}/{len(schemes)}] {name} ({code})")

        try:
            meta_resp = requests.get(f"{MFAPI_BASE}/mf/{code}/latest", headers=HEADERS, timeout=10)
            if meta_resp.status_code == 200:
                meta = meta_resp.json().get("meta", {})
                # Print first scheme’s metadata for validation
                if not first_print_done:
                    print(f"  Sample metadata: {json.dumps(meta, indent=2)}")
                    first_print_done = True
                update_metadata(cur, code, meta)
                conn.commit()
            else:
                print(f"  Metadata API returned {meta_resp.status_code}: {meta_resp.text[:200]}")
        except Exception as e:
            print(f"  Metadata fetch error: {e}")

        navs_added = fetch_nav_history(code)
        print(f"  -> Added {navs_added} NAV records")
        total_navs += navs_added
        time.sleep(0.3)

    cur.close()
    conn.close()
    print(f"\nDone. Total NAV records added: {total_navs}")

if __name__ == "__main__":
    main()