CREATE TABLE transactions
(
  id          int unsigned primary key,
  txHash      varchar(255) not null unique,
  nonce       int unsigned not null,
  fromAddress varchar(255) not null,
  toAddress   varchar(255) not null,
  value       varchar(255) not null,
  payload     varchar(255) not null,
  v           varchar(255) not null,
  r           varchar(255) not null,
  s           varchar(255) not null,
  gasUsed     varchar(255) not null,
  gasPrice    varchar(255) not null,
  blockNumber int unsigned not null,
  blockHash   varchar(255) not null,
  location    int unsigned not null
);

CREATE INDEX `from_address_index` ON transactions (fromAddress);
CREATE INDEX `to_address_index` ON transactions (toAddress);

