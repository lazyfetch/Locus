import requests
import os
import json
import re
import psycopg2
from datetime import datetime, date
from bs4 import BeautifulSoup

with open("config.json", "r", encoding="utf-8") as f:
    cfg = json.load(f)

DB_CONFIG = cfg["DB_CONFIG"]
HEADERS = cfg["HEADERS"]
BASE_URL = cfg["BASE_URL"]


MANUAL_SLUGS = {}
SLUG_FILE = "manual_slugs.json"
if os.path.exists(SLUG_FILE):
    with open(SLUG_FILE, "r", encoding="utf-8") as f:
        MANUAL_SLUGS = json.load(f)


def slug_variations(scheme_name):
    clean = re.sub(r'[^a-zA-Z0-9\s]', ' ', scheme_name.lower())
    words = clean.split()
    noise = {'option', 'scheme', 'plan', 'a', 'an', 'the', 'for'}
    base = [w for w in words if w not in noise]

    def make_slug(word_list):
        return re.sub(r'-+', '-', '-'.join(word_list)).strip('-')

    slugs = []

    # Variation 1: direct before growth 
    v1 = list(base)
    if 'direct' in v1 and 'growth' in v1:
        v1.remove('growth')
        v1.remove('direct')
        v1.append('direct')
        v1.append('growth')
    elif 'direct' in v1:
        v1.remove('direct')
        v1.append('direct')
    slugs.append(make_slug(v1))

    # Variation 2: with 'plan' appended (some debt funds)
    slugs.append(make_slug(v1 + ['plan']))

    # Variation 3: with 'growth' appended (if missing)
    slugs.append(make_slug(v1 + ['growth']))

    # Variation 4: direct-plan-growth pattern 
    v4 = list(base)
    if 'direct' in v4 and 'growth' in v4:
        v4.remove('growth')
        v4.remove('direct')
        v4.append('direct')
        v4.append('plan')
        v4.append('growth')
    elif 'direct' in v4:
        v4.remove('direct')
        v4.append('direct')
        v4.append('plan')
        v4.append('growth')
    slugs.append(make_slug(v4))

    # Variation 5: keep plan, drop growth, direct at end
    v5 = list(base)
    if 'direct' in v5:
        v5.remove('direct')
        v5.append('direct')
    v5 = [w for w in v5 if w != 'growth'] + ['plan']
    slugs.append(make_slug(v5))

    # Variation 6: remove 'fund' word entirely 
    v6 = [w for w in v1 if w not in ('fund',)]
    slugs.append(make_slug(v6))

    # Deduplicate while preserving order
    seen = set()
    unique = []
    for s in slugs:
        if s and s not in seen:
            unique.append(s)
            seen.add(s)
    return unique

def extract_holdings_from_page(scheme_name):
    """Returns (portfolio_date, holdings_list) or (None, []) if fails."""

    # First check manual override
    manual_slug = MANUAL_SLUGS.get(scheme_name)
    if manual_slug:
        slugs = [manual_slug]
        print("  Using manual slug override")
    else:
        slugs = slug_variations(scheme_name)

    for slug in slugs:
        url = BASE_URL + slug
        try:
            resp = requests.get(url, headers=HEADERS, timeout=15)
            if resp.status_code != 200:
                continue
            soup = BeautifulSoup(resp.text, 'html.parser')
            script = soup.find('script', id='__NEXT_DATA__')
            if not script:
                continue
            data = json.loads(script.string)

            def find_key(obj, key):
                if isinstance(obj, dict):
                    if key in obj:
                        return obj[key]
                    for v in obj.values():
                        res = find_key(v, key)
                        if res is not None:
                            return res
                elif isinstance(obj, list):
                    for item in obj:
                        res = find_key(item, key)
                        if res is not None:
                            return res
                return None

            holdings = find_key(data, 'holdings')
            if not holdings or not isinstance(holdings, list):
                continue

            port_date_str = holdings[0].get('portfolio_date') if holdings else None
            port_date = None
            if port_date_str:
                try:
                    port_date = datetime.strptime(port_date_str[:10], "%Y-%m-%d").date()
                except:
                    port_date = date.today()
            else:
                port_date = date.today()

            items = []
            for h in holdings:
                name = h.get('company_name') or h.get('stock_name') or ''
                sector = h.get('sector_name') or ''
                perc = h.get('corpus_per') or h.get('percentage') or 0.0
                try:
                    perc = float(perc)
                except:
                    perc = 0.0
                if name and perc > 0:
                    items.append({
                        'stock_name': name.strip()[:200],
                        'sector_name': sector.strip()[:200],
                        'percentage': perc
                    })
            return port_date, items

        except Exception:
            continue

    print(f"  All slugs failed. Tried: {slugs[0] if slugs else 'none'}")
    return None, []


def update_holdings_in_db(scheme_code, portfolio_date, holdings):
    conn = psycopg2.connect(**DB_CONFIG)
    cur = conn.cursor()
    cur.execute("DELETE FROM mf_holdings WHERE scheme_code = %s AND holding_date = %s",
                (scheme_code, portfolio_date))
    for h in holdings:
        cur.execute("""
            INSERT INTO mf_holdings (scheme_code, holding_date, stock_name, sector_name, percentage)
            VALUES (%s, %s, %s, %s, %s)
        """, (scheme_code, portfolio_date, h['stock_name'], h['sector_name'], h['percentage']))
    conn.commit()
    cur.close()
    conn.close()


def main():
    conn = psycopg2.connect(**DB_CONFIG)
    cur = conn.cursor()
    cur.execute("""
        SELECT scheme_code, scheme_name
        FROM mf_scheme
        WHERE fund_house = 'HDFC Mutual Fund'
          AND is_locus_target = TRUE
    """)
    schemes = cur.fetchall()
    cur.close()
    conn.close()
    print(f"Found {len(schemes)} HDFC target schemes.\n")

    success = []
    failed = []

    for code, name in schemes:
        print(f"Processing: {name}")
        port_date, holdings = extract_holdings_from_page(name)
        if not holdings:
            failed.append(name)
            continue
        update_holdings_in_db(code, port_date, holdings)
        success.append(name)
        print(f"  -> Inserted {len(holdings)} holdings")

    print("\n" + "=" * 60)
    print(f"SUCCESS: {len(success)} / {len(schemes)}")
    print(f"FAILED:  {len(failed)} / {len(schemes)}")
    if failed:
        print("\nFailed funds:")
        for f in failed:
            print(f"  - {f}")


if __name__ == "__main__":
    main()