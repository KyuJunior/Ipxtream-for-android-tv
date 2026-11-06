# Game leaderboard server

Live at https://xtream-leaderboard.yaseralhasany2.workers.dev (owner's Cloudflare account). A Cloudflare Worker with a D1 database. It holds each player's best score per game and serves the top 50.
The app reads the address from `LEADERBOARD_URL` in `app/build.gradle.kts`. While that is empty, the app
keeps scores on the device only.

## First deploy (owner, once)

Run these in this folder (`server/leaderboard`):

```bash
npm install
npx wrangler login                                  # opens the browser: sign in to Cloudflare
npx wrangler d1 create xtream-leaderboard           # prints a database_id: paste it into wrangler.toml
npx wrangler d1 execute xtream-leaderboard --remote --file=schema.sql
npx wrangler deploy                                 # prints the https://xtream-leaderboard.<you>.workers.dev address
```

Then put that address in `LEADERBOARD_URL` (both flavors) and release the app.

## Removing a name

Names pass a word filter (`src/rules.js`, kept in step with `GameNameFilter.kt` in the app). To remove one that got through:

```bash
npx wrangler d1 execute xtream-leaderboard --remote --command "DELETE FROM scores WHERE name = 'the name'"
```

## Tests

`npm test` checks the score and name rules.
