import requests
import json
import re
import psycopg2
from bs4 import BeautifulSoup
from pathlib import Path

CONFIG_PATH = Path(__file__).parent.parent / "config.json"
with open(CONFIG_PATH, "r", encoding="utf-8") as f:
    cfg = json.load(f)

DB_CONFIG = cfg["DB_CONFIG"]
HEADERS = cfg["HEADERS"]
BASE = cfg["BASE_URL"]

MANUAL_SLUGS = {}
slug_file = Path(__file__).parent / "manual_urls_ppfas.json"
if slug_file.exists():
    with open(slug_file, "r", encoding="utf-8") as f:
        MANUAL_SLUGS = json.load(f)
    print(f"Loaded {len(MANUAL_SLUGS)} manual slugs from {slug_file.name}")
else:
    print("No manual slugs file found – using automatic slug generation only.")

def slug_variations(scheme_name):
    clean = re.sub(r'[^a-zA-Z0-9\s]', ' ', scheme_name.lower())
    words = clean.split()
    noise = {'option', 'scheme', 'plan', 'a', 'an', 'the', 'for'}
    base = [w for w in words if w not in noise]

    def make_slug(word_list):
        return re.sub(r'-+', '-', '-'.join(word_list)).strip('-')

    slugs = []
    v1 = list(base)
    if 'direct' in v1 and 'growth' in v1:
        v1.remove('growth'); v1.remove('direct')
        v1.append('direct'); v1.append('growth')
    elif 'direct' in v1:
        v1.remove('direct'); v1.append('direct')
    slugs.append(make_slug(v1))
    slugs.append(make_slug(v1 + ['plan']))
    slugs.append(make_slug(v1 + ['growth']))

    v4 = list(base)
    if 'direct' in v4 and 'growth' in v4:
        v4.remove('growth'); v4.remove('direct')
        v4.append('direct'); v4.append('plan'); v4.append('growth')
    elif 'direct' in v4:
        v4.remove('direct'); v4.append('direct'); v4.append('plan'); v4.append('growth')
    slugs.append(make_slug(v4))

    v5 = list(base)
    if 'direct' in v5:
        v5.remove('direct'); v5.append('direct')
    v5 = [w for w in v5 if w != 'growth'] + ['plan']
    slugs.append(make_slug(v5))

    v6 = [w for w in v1 if w not in ('fund',)]
    slugs.append(make_slug(v6))

    seen = set()
    unique = []
    for s in slugs:
        if s and s not in seen:
            unique.append(s)
            seen.add(s)
    return unique

def hunt_for_managers(data):
    found = []
    def search(node):
        if isinstance(node, dict):
            if "name" in node and ("experience" in node or "designation" in node or "education" in node):
                found.append(node["name"])
            elif "manager_name" in node:
                found.append(node["manager_name"])
            for key, value in node.items():
                if "manager" in key.lower() and isinstance(value, str) and len(value) > 3:
                    found.append(value)
                else:
                    search(value)
        elif isinstance(node, list):
            for item in node:
                search(item)
    search(data)
    unique = list(set([m.strip() for m in found if m and len(m) < 50]))
    return ", ".join(unique)

def extract_metadata_from_page(scheme_name):
    manual_slug = MANUAL_SLUGS.get(scheme_name)
    slugs = [manual_slug] if manual_slug else slug_variations(scheme_name)

    for slug in slugs:
        url = BASE + slug
        try:
            resp = requests.get(url, headers=HEADERS, timeout=15)
            if resp.status_code != 200:
                continue
            soup = BeautifulSoup(resp.text, 'html.parser')
            script = soup.find('script', id='__NEXT_DATA__')
            if not script:
                continue
            data = json.loads(script.string)

            page_props = data.get("props", {}).get("pageProps", {})
            fund_data = page_props.get("mfFundObj") or page_props.get("mfServerSideData", {})
            if not fund_data:
                continue

            aum = fund_data.get("aum")
            expense_raw = fund_data.get("expense_ratio")
            benchmark = fund_data.get("benchmark_name")
            exit_load = fund_data.get("exit_load")
            managers = hunt_for_managers(data)

            expense_num = None
            if expense_raw:
                try:
                    expense_num = float(expense_raw.replace('%', '').strip())
                except:
                    pass

            return {
                "aum_cr": aum,
                "expense_ratio_direct": expense_num,
                "benchmark_index": benchmark,
                "fund_managers": managers,
                "exit_load": exit_load
            }
        except Exception:
            continue

    print(f"  All slugs failed for {scheme_name}")
    return None

def update_scheme_meta(cur, scheme_code, meta):
    updates = []
    params = []

    if meta.get("aum_cr") is not None:
        updates.append("aum_cr = %s")
        params.append(meta["aum_cr"])
    if meta.get("expense_ratio_direct") is not None:
        updates.append("expense_ratio_direct = %s")
        params.append(meta["expense_ratio_direct"])
    if meta.get("benchmark_index"):
        updates.append("benchmark_index = %s")
        params.append(meta["benchmark_index"])
    if meta.get("fund_managers"):
        updates.append("fund_managers = %s")
        params.append(meta["fund_managers"])
    if meta.get("exit_load"):
        updates.append("exit_load = %s")
        params.append(meta["exit_load"])

    if updates:
        params.append(scheme_code)
        cur.execute(f"UPDATE mf_scheme SET {', '.join(updates)} WHERE scheme_code = %s", params)

def main():
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

    success, failed = [], []
    for code, name in schemes:
        print(f"Processing: {name}")
        meta = extract_metadata_from_page(name)
        if meta is None:
            failed.append(name)
            continue
        update_scheme_meta(cur, code, meta)
        conn.commit()
        success.append(name)
        print(f"  -> AUM: {meta.get('aum_cr')}, Exp: {meta.get('expense_ratio_direct')}%, Benchmark: {meta.get('benchmark_index')}, Managers: {meta.get('fund_managers')}")

    cur.close()
    conn.close()

    print("\n" + "=" * 70)
    print(f"SUCCESS: {len(success)} / {len(schemes)}")
    print(f"FAILED:  {len(failed)} / {len(schemes)}")
    if failed:
        print("\nFailed funds:")
        for f in failed:
            print(f"  - {f}")

if __name__ == "__main__":
    main()