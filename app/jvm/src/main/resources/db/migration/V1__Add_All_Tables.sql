CREATE TABLE transactions
(
  id          int unsigned primary key auto_increment,
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

CREATE TABLE blocks
(
  blockHash        varchar(255) primary key,
  parentHash       varchar(255)  not null,
  ommersHash       varchar(255)  not null,
  beneficiary      varchar(255)  not null,
  stateRoot        varchar(255)  not null,
  transactionsRoot varchar(255)  not null,
  receiptsRoot     varchar(255)  not null,
  logsBloom        varchar(1024) not null,
  difficulty       varchar(255)  not null,
  blockNumber      int unsigned  not null,
  gasLimit         varchar(255)  not null,
  gasUsed          varchar(255)  not null,
  unixTimestamp    varchar(255)  not null,
  extra            varchar(255)  not null
);

CREATE INDEX `from_address_index` ON transactions (fromAddress);
CREATE INDEX `to_address_index` ON transactions (toAddress);

