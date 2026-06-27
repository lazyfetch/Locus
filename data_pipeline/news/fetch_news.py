import json
import time
import re
from datetime import datetime
from pathlib import Path

import feedparser
import psycopg2
import schedule
from sentence_transformers import SentenceTransformer

CONFIG_PATH = Path(__file__).parent.parent / "config.json"
NEWS_CONFIG_PATH = Path(__file__).parent / "news_config.json"

if not NEWS_CONFIG_PATH.exists():
    raise FileNotFoundError(
        f"Missing {NEWS_CONFIG_PATH}. "
        "Create it based on the template (see comments in code)."
    )

with open(CONFIG_PATH, "r", encoding="utf-8") as f:
    cfg = json.load(f)

with open(NEWS_CONFIG_PATH, "r", encoding="utf-8") as f:
    news_cfg = json.load(f)

DB_CONFIG = cfg["DB_CONFIG"]
EMBED_MODEL_NAME = cfg.get("EMBED_MODEL_NAME", "sentence-transformers/all-MiniLM-L6-v2")

RSS_FEEDS = news_cfg["rss_feeds"]       # list of [url, label]
KEYWORD_WORDS = set(news_cfg["keywords"])   # convert to set for fast lookup

print("Loading embedding model...")
embed_model = SentenceTransformer(EMBED_MODEL_NAME)
print(f"Model loaded: {EMBED_MODEL_NAME}")

def extract_keywords(text):
    text_lower = text.lower()
    found = []
    for kw in KEYWORD_WORDS:
        if kw in text_lower:
            found.append(kw)
    return found[:10]

def fetch_and_store():
    conn = psycopg2.connect(**DB_CONFIG)
    cur = conn.cursor()
    total_new = 0

    for feed_url, source_name in RSS_FEEDS:
        print(f"\n📡 Fetching: {source_name}")
        try:
            feed = feedparser.parse(feed_url)
        except Exception as e:
            print(f"  ❌ Parse error: {e}")
            continue

        entries = feed.entries[:25]
        print(f"  {len(entries)} entries")

        for entry in entries:
            headline = entry.get("title", "").strip()
            if not headline:
                continue

            summary = entry.get("description") or entry.get("summary", "")
            summary = re.sub(r"<[^>]+>", "", summary).strip()
            if len(summary) > 500:
                summary = summary[:497] + "..."

            url = entry.get("link", "")
            if not url:
                continue

            try:
                published_at = datetime(*entry.published_parsed[:6])
            except Exception:
                published_at = datetime.now()

            # Deduplicate by URL
            cur.execute("SELECT 1 FROM market_news WHERE url = %s", (url,))
            if cur.fetchone():
                continue

            keywords = extract_keywords(f"{headline} {summary}")
            embed_text = f"{headline} {summary}"
            embedding = embed_model.encode(embed_text).tolist()

            cur.execute(
                """
                INSERT INTO market_news
                    (headline, summary, url, source, published_at, keywords, embedding)
                VALUES (%s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (url) DO NOTHING
                """,
                (headline, summary, url, source_name, published_at, keywords, embedding)
            )
            if cur.rowcount:
                total_new += 1

        conn.commit()
        time.sleep(1)

    cur.close()
    conn.close()
    print(f"\n✅ Total new articles inserted: {total_new}")
    return total_new

def job():
    print(f"\n{'='*50}")
    print(f"Running news fetch at {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    try:
        fetch_and_store()
    except Exception as e:
        print(f"❌ Job failed: {e}")

if __name__ == "__main__":
    import sys
    if len(sys.argv) > 1 and sys.argv[1] == "--once":
        fetch_and_store()
    else:
        print("Starting news scheduler (3x daily: 08:00, 13:00, 18:00)")
        schedule.every().day.at("08:00").do(job)
        schedule.every().day.at("13:00").do(job)
        schedule.every().day.at("18:00").do(job)

        job()  # run immediately on start

        while True:
            schedule.run_pending()
            time.sleep(60)