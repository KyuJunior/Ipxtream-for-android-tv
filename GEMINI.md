# Antigravity Instructions

1. At the start and end of every task read and follow `AGENTS.md` (repo root). It holds the rules, the
   "Current state" summary and the full message log of everything built so far (Claude's and yours). Read it all.
2. Use the code knowledge graph (graphify) before reading code broadly:
   - Graph files (local, gitignored): `graphify-out/graph.json`, `graphify-out/GRAPH_REPORT.md` (read this first:
     main components and how they connect), `graphify-out/graph.html`, Obsidian vault `graphify-out/obsidian/`.
   - CLI: `C:\Users\<user>\.local\bin\graphify.exe` (not on PATH; set `PYTHONIOENCODING=utf-8`):
     `graphify query "<question>" --budget 2000`, `graphify explain "<Symbol>"`, `graphify path "A" "B"`,
     `graphify affected "<Symbol>"` (what a change impacts).
   - After code changes: `graphify update app/src`, move files from `app/src/graphify-out/` up into `graphify-out/`
     (delete the nested folder), then `graphify export html` and
     `graphify export obsidian --graph graphify-out/graph.json`. Never commit `graphify-out/`.
   - If the graph is missing on your machine, build it: `graphify update app/src`.
3. Memory: this project's shared memory is `AGENTS.md` (log + state) and the graph. Append a log entry when done.
4. Never write secrets in tracked files (`SECRETS.local.md` is local only). Commit only your own files, message
   prefix `[antigravity]`.
