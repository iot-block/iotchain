CREATE TABLE transactions
(
  txHash      varchar(255) not null,
  nonce       int unsigned not null,
  fromAddress varchar(255) not null,
  toAddress   varchar(255) not null,
  value       varchar(255) not null,
  payload     text         not null,
  v           varchar(255) not null,
  r           varchar(255) not null,
  s           varchar(255) not null,
  gasUsed     varchar(255) not null,
  gasPrice    varchar(255) not null,
  blockHash   varchar(255) not null,
  blockNumber bigint unsigned not null,
  location    int unsigned not null,
  primary key (blockHash, txHash)
);

CREATE INDEX `from_address_index` ON transactions (fromAddress);
CREATE INDEX `to_address_index` ON transactions (toAddress);

CREATE TABLE blocks
(
  blockNumber bigint unsigned not null unique,
  blockHash varchar(255) not null unique
);

CREATE INDEX `block_number_index` ON blocks (blockNumber);
