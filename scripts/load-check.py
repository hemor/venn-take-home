#!/usr/bin/env python3
"""Fires concurrent and repeated requests at a running instance and checks the decisions.

Usage: scripts/load-check.py [base-url]    (default http://localhost:8080)
Every run uses fresh customer and load ids, so it can be repeated against the same instance.
"""
import json
import sys
import time
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080").rstrip("/")
RUN = uuid.uuid4().hex[:8]
DAY = "2018-01-01T12:00:00Z"
THREADS = 200


def post(load_id, customer, amount):
    body = json.dumps({"id": load_id, "customer_id": customer, "load_amount": amount, "time": DAY}).encode()
    request = urllib.request.Request(BASE + "/v1/loads", body, {"Content-Type": "application/json"})
    started = time.monotonic()
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            payload = json.loads(response.read() or "null")
            return response.status, payload, time.monotonic() - started
    except urllib.error.HTTPError as e:
        return e.code, None, time.monotonic() - started


def fire(requests):
    with ThreadPoolExecutor(THREADS) as pool:
        return list(pool.map(lambda r: post(*r), requests))


def accepted(results):
    return sum(1 for status, payload, _ in results if status == 200 and payload["accepted"])


def statuses(results):
    counts = {}
    for status, _, _ in results:
        counts[status] = counts.get(status, 0) + 1
    return counts


failures = []


def check(name, ok, detail):
    print(f"{'PASS' if ok else 'FAIL'}  {name}: {detail}")
    if not ok:
        failures.append(name)


def a_same_customer_count_limit():
    results = fire([(f"{RUN}-a{i}", f"{RUN}-a", "$100.00") for i in range(500)])
    check("A 500 loads, one customer", accepted(results) == 3 and statuses(results) == {200: 500},
          f"accepted={accepted(results)} (want 3), statuses={statuses(results)}")


def b_same_customer_amount_limit():
    results = fire([(f"{RUN}-b{i}", f"{RUN}-b", "$5000.00") for i in range(500)])
    check("B 500 loads of $5,000, one customer", accepted(results) == 1 and statuses(results) == {200: 500},
          f"accepted={accepted(results)} (want 1), statuses={statuses(results)}")


def c_many_customers():
    results = fire([(f"{RUN}-c{i}", f"{RUN}-c{i}", "$100.00") for i in range(1000)])
    check("C 1000 customers, one load each", accepted(results) == 1000 and statuses(results) == {200: 1000},
          f"accepted={accepted(results)} (want 1000), statuses={statuses(results)}")


def d_identical_request():
    results = fire([(f"{RUN}-d", f"{RUN}-d", "$100.00")] * 200)
    check("D 200 identical requests", statuses(results) == {200: 1, 204: 199},
          f"statuses={statuses(results)} (want one 200 and 199 204)")


def e_mixed_customers():
    requests = [(f"{RUN}-e{c}-{i}", f"{RUN}-e{c}", "$1.00") for i in range(10) for c in range(300)]
    results = fire(requests)
    check("E 300 customers, 10 loads each", accepted(results) == 900 and statuses(results) == {200: 3000},
          f"accepted={accepted(results)} (want 900), statuses={statuses(results)}")


def f_hot_customer_among_cold_ones():
    hot = [(f"{RUN}-fh{i}", f"{RUN}-fh", "$100.00") for i in range(500)]
    cold = [(f"{RUN}-fc{i}", f"{RUN}-fc{i}", "$100.00") for i in range(200)]
    results = fire(hot + cold)
    hot_results, cold_results = results[:500], results[500:]
    slowest_cold = max(t for _, _, t in cold_results)
    check("F one hot customer among 200 others",
          accepted(hot_results) == 3 and accepted(cold_results) == 200,
          f"hot accepted={accepted(hot_results)} (want 3), cold accepted={accepted(cold_results)} (want 200), "
          f"slowest cold request {slowest_cold:.2f}s")


def g_retrying_a_declined_load():
    customer = f"{RUN}-g"
    first = post(f"{RUN}-g0", customer, "$5000.01")
    retries = [post(f"{RUN}-g0", customer, "$5000.01") for _ in range(50)]
    check("G 50 sequential retries of a declined load",
          first[0] == 200 and not first[1]["accepted"] and all(r[0] == 204 for r in retries),
          f"first={first[0]} accepted={first[1] and first[1]['accepted']}, retry statuses={statuses(retries)}")


if __name__ == "__main__":
    print(f"Target {BASE}, run id {RUN}")
    for scenario in (a_same_customer_count_limit, b_same_customer_amount_limit, c_many_customers,
                     d_identical_request, e_mixed_customers, f_hot_customer_among_cold_ones,
                     g_retrying_a_declined_load):
        scenario()
    sys.exit(1 if failures else 0)
