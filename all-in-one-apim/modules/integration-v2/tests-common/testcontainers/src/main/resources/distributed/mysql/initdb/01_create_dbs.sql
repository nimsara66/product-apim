DROP DATABASE IF EXISTS WSO2AM_DB;
-- WSO2 requires a CASE-SENSITIVE collation; latin1's default (latin1_swedish_ci) is case-insensitive,
-- so pin latin1_bin explicitly (the latin1 equivalent of the docs' utf8mb4_bin recommendation). The DDL
-- scripts create tables without per-table COLLATE, so they inherit this database default.
CREATE DATABASE WSO2AM_DB CHARACTER SET latin1 COLLATE latin1_bin;
DROP DATABASE IF EXISTS WSO2AM_SHARED_DB;
CREATE DATABASE WSO2AM_SHARED_DB CHARACTER SET latin1 COLLATE latin1_bin;
CREATE USER IF NOT EXISTS 'wso2carbon'@'%' IDENTIFIED BY 'wso2carbon';
GRANT ALL PRIVILEGES ON WSO2AM_DB.* TO 'wso2carbon'@'%';
GRANT ALL PRIVILEGES ON WSO2AM_SHARED_DB.* TO 'wso2carbon'@'%';
FLUSH PRIVILEGES;
