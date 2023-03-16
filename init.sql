CREATE DATABASE lets_automate;
CREATE USER default_user WITH PASSWORD 'password';
GRANT ALL PRIVILEGES ON DATABASE lets_automate to default_user;

ALTER DATABASE lets_automate OWNER to default_user;
GRANT USAGE, CREATE ON SCHEMA PUBLIC TO default_user;

CREATE DATABASE lets_automate_test;
CREATE USER user_test WITH PASSWORD 'password_test';
GRANT ALL PRIVILEGES ON DATABASE lets_automate_test to user_test;

ALTER DATABASE lets_automate_test OWNER to user_test;
GRANT USAGE, CREATE ON SCHEMA PUBLIC TO user_test;