import psycopg2
import json
from sentence_transformers import SentenceTransformer

with open("config.json") as f:
    cfg = json.load(f)
DB = cfg["DB_CONFIG"]

model = SentenceTransformer("all-MiniLM-L6-v2")

conn = psycopg2.connect(
    dbname=DB["dbname"],
    user=DB["user"],
    password=DB["password"],
    host=DB["host"],
    port=DB["port"],
)

fetch_cur = conn.cursor()
update_cur = conn.cursor()

fetch_cur.execute("SELECT COUNT(*) FROM mf_chunks WHERE embedding IS NULL")
total = fetch_cur.fetchone()[0]
print(f"Chunks needing embedding: {total}")

BATCH_SIZE = 100

fetch_cur.execute(
    "SELECT id, chunk_text FROM mf_chunks WHERE embedding IS NULL"
)

batch_ids = []
batch_texts = []
done = 0

for row in fetch_cur:
    batch_ids.append(row[0])
    batch_texts.append(row[1])

    if len(batch_ids) >= BATCH_SIZE:
        embeddings = model.encode(batch_texts, normalize_embeddings=True)

        for chunk_id, emb in zip(batch_ids, embeddings):
            update_cur.execute(
                "UPDATE mf_chunks SET embedding = %s::vector, embed_model_version = %s WHERE id = %s",
                (emb.tolist(), "all-MiniLM-L6-v2", chunk_id),
            )

        conn.commit()
        done += len(batch_ids)
        print(f"Progress: {done}/{total}")
        batch_ids = []
        batch_texts = []


if batch_ids:
    embeddings = model.encode(batch_texts, normalize_embeddings=True)
    for chunk_id, emb in zip(batch_ids, embeddings):
        update_cur.execute(
            "UPDATE mf_chunks SET embedding = %s::vector, embed_model_version = %s WHERE id = %s",
            (emb.tolist(), "all-MiniLM-L6-v2", chunk_id),
        )
    conn.commit()
    done += len(batch_ids)

fetch_cur.close()
update_cur.close()
conn.close()
print(f"Done. {done} embeddings generated.")