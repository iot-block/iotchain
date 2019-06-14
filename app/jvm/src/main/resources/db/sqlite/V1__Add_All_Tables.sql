CREATE TABLE transactions
(
  txHash      char(64) not null,
  nonce       int unsigned not null,
  fromAddress char(64) not null,
  toAddress   char(64) not null,
  value       char(64) not null,
  payload     text         not null,
  v           varchar(255) not null,
  r           varchar(255) not null,
  s           varchar(255) not null,
  gasUsed     char(64) not null,
  gasPrice    char(64) not null,
  blockHash   char(64) not null,
  blockNumber unsigned big int not null,
  location    int unsigned not null,
  primary key (blockHash, txHash)
);

CREATE INDEX `from_address_index` ON transactions (fromAddress);
CREATE INDEX `to_address_index` ON transactions (toAddress);
CREATE INDEX `tx_block_number_index` ON transactions (blockNumber);

CREATE TABLE blocks
(
  blockNumber unsigned big int not null unique,
  blockHash char(64) not null unique
);

CREATE INDEX `b_block_number_index` ON blocks (blockNumber);
