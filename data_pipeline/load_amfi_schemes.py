import requests
import psycopg2

DB_CONFIG = {
    "dbname": "locus",
    "user": "postgres",
    "password": "root",
    "host": "localhost",
    "port": 5432
}


def is_primary_scheme(scheme_name):

    name = scheme_name.upper()

    return (
        "DIRECT" in name
        and "GROWTH" in name
        and "IDCW" not in name
    )


def is_locus_target(scheme_name):

    name = scheme_name.upper()

    # Must be a primary scheme first
    if not is_primary_scheme(scheme_name):
        return False

    blacklist = [
        "FMP",
        "CLOSE ENDED",
        "CLOSED ENDED",
        "SERIES",
        "1100D",
        "1126D",
        "1170D"
    ]

    for word in blacklist:
        if word in name:
            return False

    return True


def get_amfi_schemes():

    url = "https://www.amfiindia.com/spages/NAVAll.txt"

    response = requests.get(url, timeout=30)
    response.raise_for_status()

    lines = response.text.splitlines()

    schemes = []
    current_amc = None

    for line in lines:

        line = line.strip()

        if not line:
            continue

        # AMC Header
        if ";" not in line:
            current_amc = line
            continue

        parts = line.split(";")

        # Expected:
        # Scheme Code;ISIN Div;ISIN Growth;Scheme Name;NAV;Date

        if len(parts) < 4:
            continue

        try:

            scheme_code = int(parts[0])

            isin_dividend = parts[1].strip()
            isin_growth = parts[2].strip()
            scheme_name = parts[3].strip()

            schemes.append({
                "scheme_code": scheme_code,
                "scheme_name": scheme_name,
                "fund_house": current_amc,
                "isin_growth": isin_growth,
                "isin_dividend": isin_dividend,
                "is_primary_scheme": is_primary_scheme(
                    scheme_name
                ),
                "is_locus_target": is_locus_target(
                    scheme_name
                ),
                "ingestion_status": "PENDING"
            })

        except Exception:
            continue

    return schemes


def save_to_db(schemes):

    conn = psycopg2.connect(**DB_CONFIG)
    cur = conn.cursor()

    inserted = 0
    updated = 0

    for s in schemes:

        cur.execute("""
            SELECT scheme_code
            FROM mf_scheme
            WHERE scheme_code = %s
        """, (s["scheme_code"],))

        exists = cur.fetchone()

        if exists:

            cur.execute("""
                UPDATE mf_scheme
                SET
                    scheme_name = %s,
                    fund_house = %s,
                    isin_growth = %s,
                    is_primary_scheme = %s,
                    is_locus_target = %s,
                    updated_at = CURRENT_TIMESTAMP
                WHERE scheme_code = %s
            """, (
                s["scheme_name"],
                s["fund_house"],
                s["isin_growth"],
                s["is_primary_scheme"],
                s["is_locus_target"],
                s["scheme_code"]
            ))

            updated += 1

        else:

            cur.execute("""
                INSERT INTO mf_scheme
                (
                    scheme_code,
                    scheme_name,
                    fund_house,
                    isin_growth,
                    is_primary_scheme,
                    is_locus_target,
                    ingestion_status
                )
                VALUES
                (
                    %s,
                    %s,
                    %s,
                    %s,
                    %s,
                    %s,
                    %s
                )
            """, (
                s["scheme_code"],
                s["scheme_name"],
                s["fund_house"],
                s["isin_growth"],
                s["is_primary_scheme"],
                s["is_locus_target"],
                s["ingestion_status"]
            ))

            inserted += 1

    conn.commit()

    cur.close()
    conn.close()

    print(f"Inserted: {inserted}")
    print(f"Updated: {updated}")


if __name__ == "__main__":

    schemes = get_amfi_schemes()

    print(f"Total schemes found: {len(schemes)}")

    primary_count = sum(
        1 for s in schemes
        if s["is_primary_scheme"]
    )

    locus_count = sum(
        1 for s in schemes
        if s["is_locus_target"]
    )

    print(f"Primary schemes: {primary_count}")
    print(f"Locus target schemes: {locus_count}")

    save_to_db(schemes)