#!/bin/bash

set -e
set -u

function create_user_and_database() {
	local database=offer
	echo "  Creating user and database '$database'"
	psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL

	    GRANT ALL PRIVILEGES ON DATABASE $database TO $POSTGRES_USER;

      CREATE SCHEMA offer;
      SET SEARCH_PATH = offer;

      GRANT ALL PRIVILEGES ON SCHEMA offer TO offer;
      GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA offer TO offer;
      GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA offer TO offer;

      create table output_table
      (
          id        bigint not null
              constraint output_table_pk
                  primary key,
          data_text varchar
      );





EOSQL
}

if [ -n "$POSTGRES_MULTIPLE_DATABASES" ]; then
	echo "Multiple database creation requested: $POSTGRES_MULTIPLE_DATABASES"
	for db in $(echo $POSTGRES_MULTIPLE_DATABASES | tr ',' ' '); do
		create_user_and_database $db
	done
	echo "Multiple databases created"
fi