CREATE TABLE transactions
(
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  txHash      TEXT    NOT NULL UNIQUE,
  nonce       INTEGER NOT NULL,
  fromAddress TEXT    NOT NULL,
  toAddress   TEXT    NOT NULL,
  value       TEXT    NOT NULL,
  payload     TEXT    NOT NULL,
  v           TEXT    NOT NULL,
  r           TEXT    NOT NULL,
  s           TEXT    NOT NULL,
  gasUsed     TEXT    NOT NULL,
  gasPrice    TEXT    NOT NULL,
  blockNumber INTEGER NOT NULL,
  blockHash   TEXT    NOT NULL,
  location    INTEGER NOT NULL
);

CREATE TABLE blocks
(
  hash             TEXT PRIMARY KEY,
  parentHash       TEXT    NOT NULL,
  ommersHash       TEXT    NOT NULL,
  beneficiary      TEXT    NOT NULL,
  stateRoot        TEXT    NOT NULL,
  transactionsRoot TEXT    NOT NULL,
  receiptsRoot     TEXT    NOT NULL,
  logsBloom        TEXT    NOT NULL,
  difficulty       TEXT    NOT NULL,
  number           INTEGER NOT NULL,
  gasLimit         TEXT    NOT NULL,
  gasUsed          TEXT    NOT NULL,
  unixTimestamp    TEXT    NOT NULL,
  extra            TEXT    NOT NULL
);

CREATE INDEX IF NOT EXISTS `from_address_index` ON transactions (fromAddress);
CREATE INDEX IF NOT EXISTS `to_address_index` ON transactions (toAddress);

