# One-Click-Partition-Creator-and-Storage-Manager
LAN-first Windows app to create and manage storage partitions locally and across the LAN, with centralized logging and admin-only backups.

## Features
- Partition operations: create, shrink, extend, format, delete, rename, change drive letter
- Central logging: users, machines, partitions, activity logs
- Admin-only backup using mysqldump (local + optional Google Drive folder sync)
- Offline-first on LAN; no public internet required for core features

## Tech stack
- Java 17+, Swing UI
- Windows PowerShell for disk operations
- JDBC + MySQL (current implementation)

Note: The original proposal mentioned Oracle, but this codebase currently uses MySQL/MariaDB via `com.mysql.cj.jdbc.Driver`.

## Prerequisites
- Windows 10/11 with PowerShell
- Java JDK 17+
- MySQL running locally or on LAN
- MySQL Connector/J on classpath (mysql-connector-j-9.5.0.jar is included under `src/`)

## Database setup (MySQL/MariaDB)
1) Create database and user:
```
CREATE DATABASE onclick_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'oneclickuser'@'%' IDENTIFIED BY 'Mypassword';
GRANT ALL PRIVILEGES ON onclick_db.* TO 'oneclickuser'@'%';
FLUSH PRIVILEGES;
```
2) Create required tables:
```
-- users
CREATE TABLE IF NOT EXISTS users (
  user_id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(100) UNIQUE NOT NULL,
  password VARCHAR(255) NOT NULL,
  role ENUM('ADMIN','USER') NOT NULL DEFAULT 'USER'
);

-- machines
CREATE TABLE IF NOT EXISTS machines (
  machine_id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  machine_name VARCHAR(255) NOT NULL,
  ip_address VARCHAR(64) NOT NULL,
  FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- partitions
CREATE TABLE IF NOT EXISTS partitions (
  partition_id INT AUTO_INCREMENT PRIMARY KEY,
  machine_id INT NOT NULL,
  user_id INT NOT NULL,
  drive_letter VARCHAR(10) NOT NULL,
  size_gb INT NOT NULL,
  created_date DATE NOT NULL,
  UNIQUE KEY uq_part (machine_id, drive_letter),
  FOREIGN KEY (machine_id) REFERENCES machines(machine_id) ON DELETE CASCADE,
  FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- activity_logs
CREATE TABLE IF NOT EXISTS activity_logs (
  log_id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  machine_id INT NOT NULL,
  action VARCHAR(255) NOT NULL,
  log_date TIMESTAMP NOT NULL,
  FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
  FOREIGN KEY (machine_id) REFERENCES machines(machine_id) ON DELETE CASCADE
);

```
3) (Optional) Seed an admin:
```
INSERT INTO users(username, password, role) VALUES ('admin','admin123','ADMIN');
```

## App configuration
Create `config/database.properties` in the project root:
```
db.url=jdbc:mysql://127.0.0.1:3306/onclick_db?useSSL=false&serverTimezone=UTC
db.user=oneclickuser
db.password=Mypassword
```
If the file is missing, the app falls back to `jdbc:mysql://192.168.30.225:3306/onclick_db` with the same user/password.

## Running
- In your IDE, run `gui.LoginForm` (recommended) or `TestDatabaseConnection` to verify DB connectivity
- Ensure MySQL is running and `config/database.properties` is set
- For disk operations, run the app as Administrator so PowerShell has required permissions

## Backup
- `utils.BackupManager` uses `mysqldump.exe` at `C:\\xampp\\mysql\\bin\\mysqldump.exe` by default
- Adjust `MYSQLDUMP_PATH` and backup target paths to match your environment

## Notes
- Partition operations rely on Windows PowerShell cmdlets and may require admin privileges
- LAN/remote features can be extended with socket modules in future iterations


