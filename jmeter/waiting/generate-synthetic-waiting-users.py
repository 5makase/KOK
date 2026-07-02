#!/usr/bin/env python3
import csv
import os
import random
import uuid
from pathlib import Path


POPULAR_STORE_ID = os.getenv("POPULAR_STORE_ID", "49e778c5-522c-4cfd-86a9-57f5e9b7fab4")
DEFAULT_NORMAL_STORE_IDS = [
    "cc000009-0000-0000-0000-000000000009",
    "cc000002-0000-0000-0000-000000000002",
    "cc000003-0000-0000-0000-000000000003",
    "cc000017-0000-0000-0000-000000000017",
]
NORMAL_STORE_IDS = [
    store_id.strip()
    for store_id in os.getenv("NORMAL_STORE_IDS", ",".join(DEFAULT_NORMAL_STORE_IDS)).split(",")
    if store_id.strip()
]
MESSAGES = [
    "",
    "창가 자리 가능하면 부탁드립니다",
    "아이 의자 필요합니다",
    "조용한 자리 부탁드립니다",
    "입구와 먼 자리 선호합니다",
    "알레르기 안내 필요합니다",
]


def row_for(index: int) -> dict[str, str | int]:
    if index <= 210:
        scenario_type = "POPULAR_REGISTER"
        store_id = POPULAR_STORE_ID
        arrival_delay_ms = random.randint(0, 3000)
        poll_count = random.randint(2, 5)
        poll_interval_ms = random.choice([3000, 4000, 5000])
    elif index <= 270:
        scenario_type = "NORMAL_REGISTER"
        store_id = random.choice(NORMAL_STORE_IDS)
        arrival_delay_ms = random.randint(0, 30000)
        poll_count = random.randint(1, 3)
        poll_interval_ms = random.choice([5000, 7000, 10000])
    else:
        scenario_type = "VIEW_ONLY"
        store_id = random.choice([POPULAR_STORE_ID, *NORMAL_STORE_IDS])
        arrival_delay_ms = random.randint(0, 30000)
        poll_count = random.randint(3, 6)
        poll_interval_ms = random.choice([3000, 5000, 7000])

    return {
        "userId": str(uuid.uuid4()),
        "role": "USER",
        "storeId": store_id,
        "scenarioType": scenario_type,
        "arrivalDelayMs": arrival_delay_ms,
        "peopleCount": random.randint(1, 6),
        "requestMessage": random.choice(MESSAGES),
        "pollCount": poll_count,
        "pollIntervalMs": poll_interval_ms,
    }


def main() -> None:
    random.seed(20260702)
    output = Path(__file__).resolve().parent / "synthetic-data" / "friday-peak-300.csv"
    output.parent.mkdir(parents=True, exist_ok=True)

    fieldnames = [
        "userId",
        "role",
        "storeId",
        "scenarioType",
        "arrivalDelayMs",
        "peopleCount",
        "requestMessage",
        "pollCount",
        "pollIntervalMs",
    ]
    rows = [row_for(index) for index in range(1, 301)]
    rows.sort(key=lambda row: int(row["arrivalDelayMs"]))

    with output.open("w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    print(output)


if __name__ == "__main__":
    main()
