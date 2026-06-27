import json
import psycopg2
from pathlib import Path
from sentence_transformers import SentenceTransformer

CONFIG_PATH = Path(__file__).parent.parent / "config.json"
with open(CONFIG_PATH, "r", encoding="utf-8") as f:
    cfg = json.load(f)

DB_CONFIG = cfg["DB_CONFIG"]
EMBED_MODEL_NAME = cfg.get("EMBED_MODEL_NAME", "sentence-transformers/all-MiniLM-L6-v2")


_embed_model = None

def get_model():
    global _embed_model
    if _embed_model is None:
        _embed_model = SentenceTransformer(EMBED_MODEL_NAME)
    return _embed_model


def retrieve_news(query: str, days: int = 7, top_k: int = 5) -> list[dict]:
    """
    Returns top_k recent news articles most relevant to the query.

    Args:
        query:  User question or search query
        days:   Look back this many days (default 7)
        top_k:  Number of articles to return (default 5)

    Returns:
        List of dicts with keys: headline, summary, url, source, published_at, similarity
    """
    model = get_model()
    query_embedding = model.encode(query).tolist()

    conn = psycopg2.connect(**DB_CONFIG)
    cur = conn.cursor()
    cur.execute(
        """
        SELECT headline, summary, url, source, published_at,
               1 - (embedding <=> %s::vector) AS similarity
        FROM market_news
        WHERE published_at >= NOW() - INTERVAL '%s days'
          AND embedding IS NOT NULL
        ORDER BY embedding <=> %s::vector
        LIMIT %s
        """,
        (query_embedding, days, query_embedding, top_k)
    )
    rows = cur.fetchall()
    cur.close()
    conn.close()

    results = []
    for row in rows:
        results.append({
            "headline": row[0],
            "summary": row[1],
            "url": row[2],
            "source": row[3],
            "published_at": row[4].isoformat() if row[4] else None,
            "similarity": round(float(row[5]), 4),
        })
    return results


def format_news_for_context(news_list: list[dict]) -> str:
    """
    Formats retrieved news into a string suitable for LLM context injection.
    """
    if not news_list:
        return ""

    lines = ["## Recent Market News (contextual)"]
    for i, n in enumerate(news_list, 1):
        lines.append(f"{i}. **{n['headline']}** ({n['source']}, {n['published_at'][:10]})")
        if n.get("summary"):
            lines.append(f"   {n['summary'][:300]}")
        lines.append("")
    return "\n".join(lines)


if __name__ == "__main__":
    test_query = "How will RBI interest rate decision affect large cap mutual funds?"
    print(f"Query: {test_query}\n")
    news = retrieve_news(test_query, days=30, top_k=3)
    for n in news:
        print(f"[{n['source']}] {n['headline']} (sim={n['similarity']})")
        print(f"  {n['summary'][:200]}...")
        print()



