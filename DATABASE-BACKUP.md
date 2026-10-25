# Back Up the EduPoa Database to Your Machine

The database is MariaDB running in the `edupoa-db` container on the EC2 server. This guide dumps
it on the server and downloads the dump to your Windows machine.

- **Server:** `44.195.19.44`, SSH: `ssh -i "C:\Users\USER\Downloads\Eduapp.pem" ubuntu@44.195.19.44`
- **DB container:** `edupoa-db` (MariaDB 11)
- **Database name:** `bureti`
- **Credentials:** user `root`, password `root` (from `/opt/edupoa/.env` → `DB_PASSWORD`)

> If you changed `DB_PASSWORD` in `/opt/edupoa/.env`, use that value instead of `root` below.

---

## Prompt (give this to an AI agent, or follow it yourself)

You are backing up a MariaDB database that runs inside a Docker container on a remote EC2
server, and downloading the backup to the local Windows machine. Do it safely and verify the
result.

Facts:
- SSH: `ssh -i "C:\Users\USER\Downloads\Eduapp.pem" ubuntu@44.195.19.44`
- DB runs in container `edupoa-db`, database `bureti`, user `root`, password `root`.
- Use `mariadb-dump` (or `mysqldump`) executed inside the container via `docker exec`.
- Produce a single compressed SQL dump named with a timestamp, e.g. `bureti-YYYYMMDD-HHMMSS.sql.gz`.
- Download it to `C:\Users\USER\Desktop\EP\db-backups\` on the local machine.
- Do NOT print database contents or the password in logs. Verify the downloaded file is
  non-empty and gzip-valid. This is a read-only backup — it must not modify the live database.

Steps:
1. On the server, dump the DB from the container to a file in the home directory, gzipped.
2. Download that file to the local backups folder via `scp`.
3. Verify the local file exists and has a sensible size, then optionally delete the server-side
   copy to save disk.

---

## Ready-to-run commands (Windows PowerShell)

### 1. Create the dump on the server (inside the container) and gzip it

```powershell
ssh -i "C:\Users\USER\Downloads\Eduapp.pem" ubuntu@44.195.19.44 "docker exec edupoa-db sh -c 'mariadb-dump -uroot -proot --single-transaction --routines --triggers --events --databases bureti' | gzip > ~/bureti-$(date +%Y%m%d-%H%M%S).sql.gz && ls -lh ~/bureti-*.sql.gz | tail -1"
```

Notes:
- `--single-transaction` gives a consistent snapshot without locking the app out.
- `--routines --triggers --events` include stored routines, triggers, and scheduled events.
- `--databases bureti` includes the `CREATE DATABASE` / `USE` statements so you can restore
  cleanly onto a fresh server.
- If `mariadb-dump` isn't found in the image, use `mysqldump` instead (same flags).

### 2. Create a local folder and download the dump

```powershell
New-Item -ItemType Directory -Force -Path "C:\Users\USER\Desktop\EP\db-backups" | Out-Null

# Copy the newest dump from the server to your machine
scp -i "C:\Users\USER\Downloads\Eduapp.pem" ubuntu@44.195.19.44:'~/bureti-*.sql.gz' "C:\Users\USER\Desktop\EP\db-backups\"
```

### 3. Verify the download

```powershell
Get-ChildItem "C:\Users\USER\Desktop\EP\db-backups\bureti-*.sql.gz" | Sort-Object LastWriteTime | Select-Object -Last 1 | Format-List Name, Length, LastWriteTime
```

A healthy dump is typically tens of KB to many MB depending on data. A near-zero size means the
dump failed (check the password / container name).

### 4. (Optional) Remove the server-side copy to reclaim disk

```powershell
ssh -i "C:\Users\USER\Downloads\Eduapp.pem" ubuntu@44.195.19.44 "rm -f ~/bureti-*.sql.gz && echo cleaned"
```

---

## One-shot version (dump straight to your machine, nothing left on server)

Streams the dump over SSH and writes the gzip locally — no file stored on the server:

```powershell
New-Item -ItemType Directory -Force -Path "C:\Users\USER\Desktop\EP\db-backups" | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
ssh -i "C:\Users\USER\Downloads\Eduapp.pem" ubuntu@44.195.19.44 "docker exec edupoa-db mariadb-dump -uroot -proot --single-transaction --routines --triggers --events --databases bureti | gzip" > "C:\Users\USER\Desktop\EP\db-backups\bureti-$stamp.sql.gz"
```

> PowerShell writes the redirected stream as-is (binary gzip). If your PowerShell version mangles
> the encoding, prefer the three-step method above (dump on server, then `scp`).

---

## How to restore this backup later

Onto the same or a new server running the stack:

```bash
# copy the dump up to the server first, then:
gunzip -c bureti-YYYYMMDD-HHMMSS.sql.gz | docker exec -i edupoa-db mariadb -uroot -proot
```

Because the dump used `--databases bureti`, it recreates/repopulates the `bureti` database.
Restore onto an empty DB to avoid duplicate-key conflicts. Restart the backend afterward:

```bash
cd /opt/edupoa && docker compose -f docker-compose.prod.yml restart app
```

---

## Automating regular backups (optional)

Add a cron job on the server to dump nightly and keep the last 7 days:

```bash
ssh -i "C:\Users\USER\Downloads\Eduapp.pem" ubuntu@44.195.19.44
mkdir -p ~/db-backups
crontab -e
```

Add this line (dumps at 02:00 daily, prunes dumps older than 7 days):

```
0 2 * * * docker exec edupoa-db mariadb-dump -uroot -proot --single-transaction --routines --triggers --events --databases bureti | gzip > ~/db-backups/bureti-$(date +\%Y\%m\%d-\%H\%M\%S).sql.gz && find ~/db-backups -name 'bureti-*.sql.gz' -mtime +7 -delete
```

Then periodically `scp` the `~/db-backups/` folder to your machine (or push to S3 for off-box
durability).

## Security notes

- The dump contains all application data. Store the local `db-backups` folder somewhere safe and
  do not commit it to git (add `db-backups/` to `.gitignore`).
- The DB password is passed on the command line via `-proot`; MariaDB warns about this. It's
  acceptable over your own SSH session, but for hardening you can put credentials in a
  `~/.my.cnf` inside the container or pass them via `MYSQL_PWD` env instead.
- Consider rotating the default `root`/`root` credentials for production.
