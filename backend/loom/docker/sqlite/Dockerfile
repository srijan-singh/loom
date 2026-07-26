# Minimal SQLite CLI container — no Java, no build tools.
#
# Usage:
#   Interactive shell against the loom db:
#     docker compose run --rm sqlite
#
#   Run a single SQL statement:
#     docker compose run --rm sqlite /data/loom.db "SELECT * FROM skills;"
#
#   Run a SQL script file (mount it alongside the db):
#     docker compose run --rm -v ./scripts:/scripts sqlite /data/loom.db < /scripts/query.sql
#
FROM alpine:3.20

RUN apk add --no-cache sqlite

VOLUME ["/data"]

ENTRYPOINT ["sqlite3"]

# Default argument — open the loom db file.
# Override by passing a different path as the first argument to `docker compose run`.
CMD ["/data/loom.db"]
