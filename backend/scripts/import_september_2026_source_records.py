"""Import the supplied Ho Tay September booking and Kiot invoice workbooks."""

from __future__ import annotations

import csv
import datetime as dt
import hashlib
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
import xml.etree.ElementTree as ET


REPO = pathlib.Path(__file__).resolve().parents[2]
BACKEND = REPO / "backend"
BRANCH_CODE = "HO_TAY"
PERIOD_START = dt.date(2026, 9, 1)
PERIOD_END = dt.date(2026, 9, 30)
BOOKING_IMPORTS = (
    ("HO_TAY", pathlib.Path(r"C:\Users\MSI\Downloads\Búc kinh Hồ Tây T9.xlsx")),
    ("HN_HOANG_CONG_CHAT", pathlib.Path(r"C:\Users\MSI\Downloads\Búc King HCC T9.xlsx")),
    ("HN_HOA_BANG", pathlib.Path(r"C:\Users\MSI\Downloads\Búc king hoa bằng t9.xlsx")),
)
INVOICE_IMPORTS = (
    ("HO_TAY", pathlib.Path(r"C:\Users\MSI\Downloads\DanhSachHoaDon_KV21112026-004943-893.xlsx")),
)
NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
NS_PKG = "http://schemas.openxmlformats.org/package/2006/relationships"
CSV_COLUMNS = (
    "branch_code", "source_system", "source_file", "source_sha256",
    "period_start", "period_end", "record_type", "source_sheet",
    "source_row", "business_date", "source_key", "payload",
)


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def text_from_cell(cell: ET.Element, shared: list[str]):
    kind = cell.attrib.get("t")
    if kind == "inlineStr":
        return "".join(node.text or "" for node in cell.iter() if local_name(node.tag) == "t")
    value = next((node.text for node in cell if local_name(node.tag) == "v"), None)
    if value is None:
        return ""
    if kind == "s":
        return shared[int(value)]
    if kind == "b":
        return value == "1"
    if kind == "str":
        return value
    try:
        number = float(value)
        return int(number) if number.is_integer() else number
    except ValueError:
        return value


def workbook_sheets(path: pathlib.Path):
    with zipfile.ZipFile(path) as archive:
        names = set(archive.namelist())
        shared: list[str] = []
        if "xl/sharedStrings.xml" in names:
            root = ET.fromstring(archive.read("xl/sharedStrings.xml"))
            shared = ["".join(node.text or "" for node in item.iter() if local_name(node.tag) == "t")
                      for item in root if local_name(item.tag) == "si"]
        workbook = ET.fromstring(archive.read("xl/workbook.xml"))
        relationships = ET.fromstring(archive.read("xl/_rels/workbook.xml.rels"))
        targets = {item.attrib["Id"]: item.attrib["Target"] for item in relationships
                   if local_name(item.tag) == "Relationship"}
        for sheet in (node for node in workbook.iter() if local_name(node.tag) == "sheet"):
            relation_id = next(value for key, value in sheet.attrib.items() if key.endswith("}id"))
            target = targets[relation_id].lstrip("/")
            if not target.startswith("xl/"):
                target = "xl/" + target
            root = ET.fromstring(archive.read(target))
            rows = []
            for row in (node for node in root.iter() if local_name(node.tag) == "row"):
                cells = {}
                for cell in (node for node in row if local_name(node.tag) == "c"):
                    reference = cell.attrib.get("r", "")
                    column = re.match(r"[A-Z]+", reference)
                    if column:
                        cells[column.group()] = text_from_cell(cell, shared)
                rows.append((int(row.attrib.get("r", len(rows) + 1)), cells))
            yield sheet.attrib["name"], rows


def parse_excel_date(value):
    if isinstance(value, (int, float)):
        return (dt.datetime(1899, 12, 30) + dt.timedelta(days=float(value))).date()
    if isinstance(value, str):
        for pattern in ("%d/%m/%Y", "%d/%m/%Y %H:%M", "%Y-%m-%d"):
            try:
                return dt.datetime.strptime(value.strip(), pattern).date()
            except ValueError:
                pass
    return None


def normalized_payload(headers: dict[str, object], cells: dict[str, object], date_columns: set[str]):
    payload = {}
    for column, value in cells.items():
        if value == "" or value is None:
            continue
        label = str(headers.get(column) or f"Cột {column}").strip()
        if column in date_columns:
            parsed = parse_excel_date(value)
            if parsed:
                value = parsed.isoformat()
        payload[label] = value
    return payload


def booking_records(path: pathlib.Path, branch_code: str):
    records = []
    file_hash = hashlib.sha256(path.read_bytes()).hexdigest()
    for sheet_name, rows in workbook_sheets(path):
        header_row = next((cells for _, cells in rows
                           if any(str(value).strip() == "STT" for value in cells.values())
                           and any(str(value).strip() == "BILL KIOTVIET" for value in cells.values())), {})
        headers = {column: str(value).strip() for column, value in header_row.items() if value}
        for row_number, cells in rows:
            checkin = parse_excel_date(cells.get("N"))
            room = str(cells.get("M") or "").strip()
            if (checkin is None or not PERIOD_START <= checkin <= PERIOD_END
                    or not re.match(r"^\d{3}(?:[- ]|$)", room) or not cells.get("F")):
                continue
            payload = normalized_payload(headers, cells, {"N", "P", "T"})
            records.append({
                "branch_code": branch_code,
                "source_system": "BOOKING_SPREADSHEET",
                "source_file": path.name,
                "source_sha256": file_hash,
                "period_start": PERIOD_START.isoformat(),
                "period_end": PERIOD_END.isoformat(),
                "record_type": "BOOKING",
                "source_sheet": sheet_name,
                "source_row": row_number,
                "business_date": checkin.isoformat(),
                "source_key": "",
                "payload": json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
            })
    return records


def kiot_records(path: pathlib.Path, branch_code: str):
    records = []
    file_hash = hashlib.sha256(path.read_bytes()).hexdigest()
    with zipfile.ZipFile(path) as archive:
        root = ET.fromstring(archive.read("xl/worksheets/sheet.xml"))
        rows = [node for node in root.iter() if local_name(node.tag) == "row"]
        if not rows:
            return records
        headers = [text_from_cell(cell, []) for cell in rows[0] if local_name(cell.tag) == "c"]
        for row_number, row in enumerate(rows[1:], start=2):
            values = [text_from_cell(cell, []) for cell in row if local_name(cell.tag) == "c"]
            if len(values) < len(headers):
                continue
            invoice_date = None
            try:
                invoice_date = dt.datetime.strptime(str(values[1]).strip(), "%d/%m/%Y %H:%M").date()
            except (IndexError, ValueError):
                continue
            if not PERIOD_START <= invoice_date <= PERIOD_END:
                continue
            payload = dict(zip(headers, values))
            for key in ("Tổng tiền hàng", "Giảm giá", "Tổng sau giảm giá", "Khách đã trả"):
                try:
                    raw = str(payload[key])
                    amount = float(raw)
                    payload[key] = int(amount) if amount.is_integer() else amount
                except (KeyError, ValueError):
                    pass
            records.append({
                "branch_code": branch_code,
                "source_system": "KIOTVIET_INVOICE_EXPORT",
                "source_file": path.name,
                "source_sha256": file_hash,
                "period_start": PERIOD_START.isoformat(),
                "period_end": PERIOD_END.isoformat(),
                "record_type": "KIOT_INVOICE",
                "source_sheet": "DanhSachHoaDon",
                "source_row": row_number,
                "business_date": invoice_date.isoformat(),
                "source_key": str(values[0]),
                "payload": json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
            })
    return records


def db_config():
    env = dict(os.environ)
    env_path = BACKEND / ".env"
    if env_path.exists():
        for line in env_path.read_text(encoding="utf-8-sig").splitlines():
            match = re.match(r"^\s*([^#=\s]+)\s*=(.*)$", line)
            if match:
                env.setdefault(match.group(1), match.group(2).strip().strip('"').strip("'"))
    url = env.get("DB_URL", "jdbc:postgresql://localhost:5432/gcmb")
    match = re.match(r"jdbc:postgresql://([^:/?]+)(?::(\d+))?/([^?]+)", url)
    if not match:
        raise RuntimeError("DB_URL không phải JDBC URL PostgreSQL hợp lệ.")
    host, port, database = match.groups()
    username = env.get("DB_USERNAME", "gcmb_dev")
    password = env.get("DB_PASSWORD")
    if not password:
        raise RuntimeError("Thiếu DB_PASSWORD trong backend/.env.")
    psql = shutil.which("psql")
    if not psql:
        candidate = pathlib.Path(r"C:\Program Files\PostgreSQL\18\bin\psql.exe")
        if not candidate.exists():
            raise RuntimeError("Không tìm thấy psql.exe.")
        psql = str(candidate)
    return psql, host, port or "5432", database, username, password


def run_import(records):
    if not records:
        raise RuntimeError("Không tìm thấy bản ghi tháng 9 hợp lệ trong hai workbook.")
    psql, host, port, database, username, password = db_config()
    schema_sql = (BACKEND / "scripts" / "source_import_schema.sql").read_text(encoding="utf-8")
    environment = os.environ.copy()
    environment["PGPASSWORD"] = password
    with tempfile.TemporaryDirectory(prefix="gcmb-source-import-") as work:
        work_dir = pathlib.Path(work)
        csv_path = work_dir / "source-import.csv"
        sql_path = work_dir / "source-import.sql"
        with csv_path.open("w", encoding="utf-8-sig", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=CSV_COLUMNS, quoting=csv.QUOTE_MINIMAL)
            writer.writeheader()
            writer.writerows(records)

        copy_path = csv_path.as_posix().replace("'", "''")
        sql = """
        BEGIN;
        """ + schema_sql + """
        CREATE TEMP TABLE source_import_stage (
          branch_code text, source_system text, source_file text, source_sha256 char(64),
          period_start date, period_end date, record_type text, source_sheet text,
          source_row integer, business_date date, source_key text, payload jsonb
        ) ON COMMIT DROP;
        """ + f"\\copy source_import_stage ({','.join(CSV_COLUMNS)}) FROM '{copy_path}' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')\n" + """
        DO $$ BEGIN
          IF EXISTS (SELECT 1 FROM source_import_stage s LEFT JOIN gcmb.branch b ON b.code=s.branch_code WHERE b.id IS NULL) THEN
            RAISE EXCEPTION 'Không tìm thấy mã cơ sở trong database.';
          END IF;
        END $$;
        WITH batch_input AS (
          SELECT DISTINCT branch_code,source_system,source_file,source_sha256,period_start,period_end,
            count(*) OVER (PARTITION BY branch_code,source_system,source_sha256) row_count
          FROM source_import_stage
        ), saved_batches AS (
          INSERT INTO gcmb.source_import_batch
            (branch_id,source_system,source_file,source_sha256,period_start,period_end,row_count)
          SELECT b.id,i.source_system,i.source_file,i.source_sha256,i.period_start,i.period_end,i.row_count
          FROM batch_input i JOIN gcmb.branch b ON b.code=i.branch_code
          ON CONFLICT (branch_id,source_system,source_sha256) DO UPDATE
            SET row_count=EXCLUDED.row_count,source_file=EXCLUDED.source_file
          RETURNING id,branch_id,source_system,source_sha256
        )
        INSERT INTO gcmb.source_import_record
          (batch_id,branch_id,record_type,source_sheet,source_row,business_date,source_key,payload)
        SELECT b.id,b.branch_id,s.record_type,s.source_sheet,s.source_row,s.business_date,
          nullif(s.source_key,''),s.payload
        FROM source_import_stage s JOIN gcmb.branch br ON br.code=s.branch_code
        JOIN saved_batches b ON b.branch_id=br.id AND b.source_system=s.source_system AND b.source_sha256=s.source_sha256
        ON CONFLICT (batch_id,source_sheet,source_row) DO UPDATE SET
          record_type=EXCLUDED.record_type,business_date=EXCLUDED.business_date,
          source_key=EXCLUDED.source_key,payload=EXCLUDED.payload;
        COMMIT;
        """
        sql_path.write_text(sql, encoding="utf-8")
        command = [psql, "-X", "-v", "ON_ERROR_STOP=1", "-h", host, "-p", port,
                   "-U", username, "-d", database, "-f", str(sql_path)]
        subprocess.run(command, env=environment, check=True)


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    for _, path in (*BOOKING_IMPORTS, *INVOICE_IMPORTS):
        if not path.is_file():
            raise FileNotFoundError(f"Không tìm thấy workbook: {path}")
    bookings = [record for branch_code, path in BOOKING_IMPORTS
                for record in booking_records(path, branch_code)]
    invoices = [record for branch_code, path in INVOICE_IMPORTS
                for record in kiot_records(path, branch_code)]
    counts = {}
    for record in bookings + invoices:
        key = (record["branch_code"], record["record_type"])
        counts[key] = counts.get(key, 0) + 1
    for (branch_code, record_type), count in sorted(counts.items()):
        print(f"{branch_code}: {count} dòng {record_type} tháng 09/2026.")
    run_import(bookings + invoices)
    print("Đã ghi dữ liệu nguồn vào gcmb.source_import_batch và gcmb.source_import_record.")
    print("Dữ liệu được lưu tách khỏi booking/bill/payment vận hành để không tạo hóa đơn hoặc phòng giả.")


if __name__ == "__main__":
    main()
