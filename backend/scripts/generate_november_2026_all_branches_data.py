"""Insert clearly marked synthetic source rows for every active branch in Nov 2026."""

from __future__ import annotations

import datetime as dt
import hashlib
import importlib.util
import json
import os
import pathlib
import random
import subprocess
import sys


HERE = pathlib.Path(__file__).resolve().parent
IMPORTER_PATH = HERE / "import_september_2026_source_records.py"
PERIOD_START = dt.date(2026, 11, 1)
PERIOD_END = dt.date(2026, 11, 21)
SOURCE_SYSTEM = "SYNTHETIC_DEMO"
VERSION = "gcmb-demo-november-2026-all-branches-v1"

spec = importlib.util.spec_from_file_location("gcmb_source_importer", IMPORTER_PATH)
importer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(importer)


def database_branches():
    psql, host, port, database, username, password = importer.db_config()
    environment = os.environ.copy()
    environment["PGPASSWORD"] = password
    query = "SELECT code,name FROM gcmb.branch WHERE status='ACTIVE' ORDER BY code"
    result = subprocess.run(
        [psql, "-X", "-A", "-t", "-F", "|", "-h", host, "-p", port, "-U", username, "-d", database, "-c", query],
        env=environment, check=True, capture_output=True, text=True, encoding="utf-8",
    )
    return [tuple(line.strip().split("|", 1)) for line in result.stdout.splitlines() if "|" in line]


def row(branch_code, source_hash, record_type, sheet, source_row, day, source_key, payload):
    return {
        "branch_code": branch_code,
        "source_system": SOURCE_SYSTEM,
        "source_file": f"generated://{VERSION}/{branch_code}",
        "source_sha256": source_hash,
        "period_start": PERIOD_START.isoformat(),
        "period_end": PERIOD_END.isoformat(),
        "record_type": record_type,
        "source_sheet": sheet,
        "source_row": source_row,
        "business_date": day.isoformat(),
        "source_key": source_key,
        "payload": json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
    }


def generate(branches):
    records = []
    day_count = (PERIOD_END - PERIOD_START).days + 1
    for branch_index, (branch_code, _branch_name) in enumerate(branches):
        branch_hash = hashlib.sha256(f"{VERSION}:{branch_code}".encode("utf-8")).hexdigest()
        rng = random.Random(f"{VERSION}:{branch_code}")
        for index in range(1, 61):
            day_number = ((index * 7 + branch_index * 3) % day_count) + 1
            day = PERIOD_START + dt.timedelta(days=day_number - 1)
            gross = rng.randrange(180, 801) * 1000
            discount = rng.choice([0, 0, 0, 10000, 20000, 30000, 50000])
            net = max(0, gross - discount)
            hour = 10 + ((index + branch_index) % 13)
            minute = rng.choice([0, 15, 30, 45])
            customer = f"Khách mô phỏng {index:03d}"
            payment_method = "Tiền mặt" if index % 3 else "Chuyển khoản"
            cash = net if payment_method == "Tiền mặt" else 0
            bank = net - cash
            invoice_code = f"DEMO-{branch_code}-202611-{index:04d}"
            records.append(row(
                branch_code, branch_hash, "KIOT_INVOICE", "generated-invoices", index, day, invoice_code,
                {
                    "Mã hóa đơn": invoice_code,
                    "Thời gian (Giờ đi)": f"{day:%d/%m/%Y} {hour:02d}:{minute:02d}",
                    "Khách hàng": customer,
                    "Tổng tiền hàng": gross,
                    "Giảm giá": discount,
                    "Tổng sau giảm giá": net,
                    "Khách đã trả": net,
                    "PHƯƠNG THỨC THANH TOÁN": payment_method,
                    "Nguồn dữ liệu": "DỮ LIỆU MÔ PHỎNG — KHÔNG PHẢI KIOTVIET",
                },
            ))
            room_number = ((index - 1) % 10) + 1
            records.append(row(
                branch_code, branch_hash, "BOOKING", "generated-bookings", index, day,
                f"DEMO-BOOKING-{branch_code}-202611-{index:04d}",
                {
                    "STT": index,
                    "TRẠNG THÁI PHÒNG": "CHECK OUT",
                    "HỌ VÀ TÊN": customer,
                    "NGUỒN KHÁCH": "Dữ liệu mô phỏng",
                    "PHÒNG ĐÃ ĐẶT": f"Phòng {room_number:02d}",
                    "NGÀY CHECK-IN": day.isoformat(),
                    "NGÀY CHECK-OUT": day.isoformat(),
                    "BILL KIOTVIET": net,
                    "TIỀN KHÁCH TT": net,
                    "TIỀN MẶT": cash,
                    "CK(TINGBOX)": bank,
                    "PHƯƠNG THỨC THANH TOÁN": payment_method,
                    "Nguồn dữ liệu": "DỮ LIỆU MÔ PHỎNG — KHÔNG PHẢI BOOKING THẬT",
                },
            ))
    return records


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    branches = database_branches()
    if not branches:
        raise RuntimeError("Không tìm thấy cơ sở đang hoạt động trong gcmb.branch.")
    records = generate(branches)
    importer.run_import(records)
    print(f"Đã insert {len(records):,} dòng mô phỏng cho {len(branches)} cơ sở đang hoạt động, từ 01–21/11/2026.")
    print("Mỗi cơ sở có 60 booking và 60 hóa đơn; nguồn được gắn SYNTHETIC_DEMO và không ghi vào bảng booking/bill/payment vận hành.")


if __name__ == "__main__":
    main()
