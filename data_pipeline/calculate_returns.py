import psycopg2
import json
from datetime import date, timedelta
from typing import Optional


with open("config.json") as f:
    cfg = json.load(f)

DB = cfg["DB_CONFIG"]

PERIODS = [
    ("1W",    timedelta(weeks=1),    False),
    ("1M",    timedelta(days=30),    False),
    ("3M",    timedelta(days=90),    False),
    ("6M",    timedelta(days=180),   False),
    ("1Y",    timedelta(days=365),   True),
    ("3Y",    timedelta(days=1095),  True),
    ("5Y",    timedelta(days=1825),  True),
]

def get_conn():
    return psycopg2.connect(
        dbname=DB["dbname"],
        user=DB["user"],
        password=DB["password"],
        host=DB["host"],
        port=DB["port"],
    )


def find_closest_nav(cursor, scheme_code: int, target_date: date) -> Optional[float]:
    
    cursor.execute(
        """
        SELECT nav
        FROM mf_nav_history
        WHERE scheme_code = %s AND nav_date <= %s
        ORDER BY nav_date DESC
        LIMIT 1
        """,
        (scheme_code, target_date),
    )
    row = cursor.fetchone()
    return float(row[0]) if row else None


def compute_return(nav_start: float, nav_end: float, years: float, annualize: bool) -> float:
    if nav_start <= 0:
        return None
    total_return = (nav_end / nav_start) - 1.0
    if annualize and years > 0:
        total_return = ((1 + total_return) ** (1.0 / years)) - 1.0
    return round(total_return * 100, 2)


def main():
    conn = get_conn()
    cur = conn.cursor()

    cur.execute("SELECT DISTINCT scheme_code FROM mf_nav_history ORDER BY scheme_code")
    scheme_codes = [row[0] for row in cur.fetchall()]

    cur.execute("SELECT MAX(nav_date) FROM mf_nav_history")
    latest_date: date = cur.fetchone()[0]

    print(f"Latest NAV date in DB: {latest_date}")
    print(f"Schemes to process: {len(scheme_codes)}")

    cur.execute("DELETE FROM mf_returns")
    print("Cleared existing mf_returns rows.")

    inserted = 0
    skipped = 0

    for scheme_code in scheme_codes:
        nav_end = find_closest_nav(cur, scheme_code, latest_date)
        if nav_end is None:
            skipped += 1
            continue

        for period_label, delta, annualize in PERIODS:
            target_start = latest_date - delta
            nav_start = find_closest_nav(cur, scheme_code, target_start)

            if nav_start is None:
                continue  

            years = delta.days / 365.25
            ret = compute_return(nav_start, nav_end, years, annualize)

            if ret is not None:
                cur.execute(
                    """
                    INSERT INTO mf_returns (scheme_code, period, fund_return_pct)
                    VALUES (%s, %s, %s)
                    ON CONFLICT (id) DO NOTHING
                    """,
                    (scheme_code, period_label, str(ret)),
                )
                inserted += 1

        conn.commit()

    cur.close()
    conn.close()
    print(f"Done. Inserted: {inserted}, Skipped schemes (no NAV): {skipped}")


if __name__ == "__main__":
    main()