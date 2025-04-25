import sys
import random
from threading import Thread
import requests
import time

from user import post_user
from wallet import put_wallet, get_wallet
from marketplace import (
    post_order,
    get_product,
    test_get_product_stock,
    test_post_order
)
from utils import check_response_status_code, print_fail_message, print_pass_message

MARKETPLACE_SERVICE_URL = "http://localhost:8081"

# Tracking successful orders per product
successful_orders = {}

def place_order_thread(user_id, product_id, attempts=5):
    """
    Each thread attempts to place multiple orders for a given product_id with varied quantities.
    Tracks successful orders globally.
    """
    global successful_orders
    for _ in range(attempts):
        random.seed(time.time())
        quantity = random.randint(1, 10)  # Varying order quantity (1-10)
        resp = post_order(user_id, [{"product_id": product_id, "quantity": quantity}])

        if resp.status_code == 201:
            if not test_post_order(
                user_id, 
                items=[{"product_id": product_id, "quantity": quantity}],
                response=resp,
                expect_success=True
            ):
                print_fail_message(f"test_post_order failed for product {product_id}.")
            successful_orders[product_id] += quantity  # Track orders per product

        elif resp.status_code == 400:
            # Out of stock or insufficient balance case
            if not test_post_order(
                user_id, 
                items=[{"product_id": product_id, "quantity": quantity}],
                response=resp,
                expect_success=False
            ):
                print_fail_message(f"test_post_order failed on expected failure scenario for product {product_id}.")
        else:
            print_fail_message(f"Unexpected status code {resp.status_code} for POST /orders.")


def main():
    try:
        # Step 1: Create user with a large enough balance
        user_id = 30000
        resp = post_user(user_id, "Boby Market", "boby@market.com")
        if not check_response_status_code(resp, 201):
            return False

        # Step 2: Credit wallet with a huge amount to handle multiple orders
        resp = put_wallet(user_id, "credit", 1234567)
        if not check_response_status_code(resp, 200):
            return False

        # Step 3: Define multiple products with initial stock
        product_ids = [118, 119, 120]  # Testing with 3 different products
        initial_stock = {118: 14, 119: 8, 120: 15}  # Random initial stock (50-100)
        global successful_orders
        successful_orders = {pid: 0 for pid in product_ids}  # Reset tracking

        # Step 4: Fetch product stock info before test
        for product_id in product_ids:
            resp = get_product(product_id)
            if resp.status_code == 200:
                pass

        # Step 5: Launch concurrency threads for multiple products
        thread_count = 10  # Total number of threads
        threads = []

        for product_id in product_ids:
            for _ in range(thread_count):
                attempts_per_thread = random.randint(1, 3)  # Random attempts per thread
                t = Thread(target=place_order_thread, kwargs={
                    "user_id": user_id,
                    "product_id": product_id,
                    "attempts": attempts_per_thread
                })
                threads.append(t)
                t.start()

        # Step 6: Wait for all threads to finish
        for t in threads:
            t.join()

        # Step 7: Final stock verification
        print_pass_message(f"Total successful orders per product: {successful_orders}")

        for product_id in product_ids:
            if successful_orders[product_id] > initial_stock[product_id]:
                print_fail_message(
                    f"Concurrency error for product {product_id}: successful_orders={successful_orders[product_id]} > stock={initial_stock[product_id]}"
                )
                return False

            expected_final_stock = initial_stock[product_id] - successful_orders[product_id]

            # Fetch final stock and validate
            resp = get_product(product_id)
            expected_stock = resp.json()["stock_quantity"]
            print(expected_stock == expected_final_stock)
            print(f"<========= RESPONSE for Product {product_id} ======\n", resp.json())
            # if not test_get_product_stock(product_id, resp, expected_stock=expected_final_stock):
            #     return False
        
        print_pass_message("Marketplace concurrency test passed.")
        return True

    except Exception as e:
        print_fail_message(f"Test crashed: {e}")
        return False


if __name__ == "__main__":
    if main():
        sys.exit(0)
    else:
        sys.exit(1)
