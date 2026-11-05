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
DAY = "2018-01-01T00:00:00Z"


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


def load(i, customer, amount="$100.00"):
    return f"{RUN}-{customer}-{i}", f"{RUN}-{customer}", amount


def accepted(results):
    return sum(1 for status, payload, _ in results if status == 200 and payload["accepted"])


def statuses(results):
    counts = {}
    for status, _, _ in results:
        counts[status] = counts.get(status, 0) + 1
    return counts


def latency(results):
    times = sorted(t for _, _, t in results)
    return (f"p50={times[len(times) // 2] * 1000:.0f}ms p99={times[int(len(times) * .99)] * 1000:.0f}ms "
            f"max={times[-1] * 1000:.0f}ms")


failures = []


def check(name, ok, detail):
    print(f"{'PASS' if ok else 'FAIL'}  {name}: {detail}")
    if not ok:
        failures.append(name)


def burst(requests, workers):
    started = time.monotonic()
    with ThreadPoolExecutor(workers) as pool:
        results = list(pool.map(lambda r: post(*r), requests))
    wall = time.monotonic() - started
    return results, f"{len(requests) / wall:.0f} req/s, {latency(results)}"


def one_customer_over_the_count_limit():
    results, stats = burst([load(i, "count") for i in range(500)], 100)
    check("500 loads of $100 for one customer (limit 3 a day)",
          accepted(results) == 3 and statuses(results) == {200: 500},
          f"accepted={accepted(results)} (want 3), statuses={statuses(results)}, {stats}")


def one_customer_over_the_amount_limit():
    results, stats = burst([load(i, "amount", "$5000.00") for i in range(200)], 100)
    check("200 loads of $5,000 for one customer (limit $5,000 a day)",
          accepted(results) == 1 and statuses(results) == {200: 200},
          f"accepted={accepted(results)} (want 1), statuses={statuses(results)}, {stats}")


def many_customers():
    results, stats = burst([load(0, f"many{i}") for i in range(1000)], 100)
    check("1000 customers, one load each",
          accepted(results) == 1000 and statuses(results) == {200: 1000},
          f"accepted={accepted(results)} (want 1000), statuses={statuses(results)}, {stats}")


def identical_request_sent_concurrently():
    request = (f"{RUN}-same", f"{RUN}-same", "$10.00")
    results, stats = burst([request] * 200, 100)
    check("the same load sent 200 times at once", statuses(results) == {200: 1, 204: 199},
          f"statuses={statuses(results)} (want one 200 and 199 204), {stats}")


def many_customers_over_the_count_limit():
    requests = [load(i, f"mixed{i % 300}") for i in range(3000)]
    results, stats = burst(requests, 300)
    per_customer = {}
    for (_, customer, _), result in zip(requests, results):
        per_customer[customer] = per_customer.get(customer, 0) + (result[0] == 200 and result[1]["accepted"])
    check("3000 loads over 300 customers (10 each, limit 3 a day)",
          set(per_customer.values()) == {3} and len(per_customer) == 300 and statuses(results) == {200: 3000},
          f"customers with exactly 3 accepted={sum(v == 3 for v in per_customer.values())} (want 300), "
          f"accepted={accepted(results)}, statuses={statuses(results)}, {stats}")


def hot_customer_among_others():
    hot = [load(i, "hot") for i in range(1000)]
    cold = [load(0, f"cold{i}") for i in range(500)]
    with ThreadPoolExecutor(200) as pool:
        hot_futures = [pool.submit(post, *r) for r in hot]
        time.sleep(0.05)
        cold_futures = [pool.submit(post, *r) for r in cold]
        hot_results = [f.result() for f in hot_futures]
        cold_results = [f.result() for f in cold_futures]
    check("1000 loads for one customer while 500 other customers load",
          accepted(hot_results) == 3 and accepted(cold_results) == 500,
          f"hot accepted={accepted(hot_results)} (want 3), cold accepted={accepted(cold_results)} (want 500), "
          f"cold {latency(cold_results)}")


def retrying_a_declined_load():
    request = load(0, "retry", "$5000.01")
    first = post(*request)
    retries = [post(*request) for _ in range(50)]
    check("50 sequential retries of a declined load",
          first[0] == 200 and not first[1]["accepted"] and all(r[0] == 204 for r in retries),
          f"first={first[0]} accepted={first[1] and first[1]['accepted']}, retry statuses={statuses(retries)}")


if __name__ == "__main__":
    print(f"Target {BASE}, run id {RUN}")
    for scenario in (one_customer_over_the_count_limit, one_customer_over_the_amount_limit, many_customers,
                     identical_request_sent_concurrently, many_customers_over_the_count_limit,
                     hot_customer_among_others, retrying_a_declined_load):
        scenario()
    sys.exit(1 if failures else 0)
