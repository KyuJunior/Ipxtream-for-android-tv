CREATE TABLE IF NOT EXISTS scores (
  game   TEXT    NOT NULL,
  player TEXT    NOT NULL,
  name   TEXT    NOT NULL,
  score  INTEGER NOT NULL,
  at     INTEGER NOT NULL,
  submitted_at INTEGER NOT NULL,
  PRIMARY KEY (game, player)
);
CREATE INDEX IF NOT EXISTS scores_by_rank ON scores (game, score DESC, at ASC);
CREATE INDEX IF NOT EXISTS scores_by_player ON scores (player);
