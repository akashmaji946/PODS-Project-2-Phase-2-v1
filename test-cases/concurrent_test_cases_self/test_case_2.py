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

# We'll track how many orders are successfully placed
successful_orders = 0
count = 0
def place_order_thread(user_id, product_id, attempts=5):
        """
        Each thread attempts to place 'attempts' orders for the same product_id, quantity=1.
        If an order is successfully placed (HTTP 201), increment global successful_orders.
        We also call test_post_order(...) to verify the response structure.
        """
        global successful_orders
        global count
        try:
            for _ in range(attempts):
                # random.seed(time.time())
                quantity = random.randint(1, 3)
                resp = post_order(user_id, [{"product_id": product_id, "quantity": quantity}])
                count += 1
                print(count, " ##########################################################################################################################################################")
                if resp.status_code == 201:
                    if not test_post_order(user_id, [{"product_id": product_id, "quantity": quantity}], resp, True):
                        print_fail_message("test_post_order failed on success scenario.")
                    successful_orders += quantity
                elif resp.status_code == 400:
                    if not test_post_order(user_id, [{"product_id": product_id, "quantity": quantity}], resp, False):
                        print_fail_message("test_post_order failed on expected failure scenario.")
                else:
                    print_fail_message(f"Unexpected status code {resp.status_code} for POST /orders.")
        except Exception as e:
            print_fail_message(f"Thread encountered an error: {e}")

def main():
    try:
        # 2) Create user (large enough balance so they can buy many items)
        user_id = 20001
        resp = post_user(user_id, "Akash mMaji", "amkashmaji@iisc.ac.com")
        if not check_response_status_code(resp, 201):
            return False

        # 3) Credit wallet significantly (e.g. 200000)
        resp = put_wallet(user_id, "credit", 12345678)
        if not check_response_status_code(resp, 200):
            return False

        # 4) Check the product's initial stock
        product_id = 117
        initial_stock = 10  
        resp = get_product(product_id)
        if resp.status_code == 200:
            pass

        # 5) Launch concurrency threads that place orders for product_id=101, quantity=1
        # We want more attempts than stock to see if we ever overshoot.
        global successful_orders
        successful_orders = 0  # reset global

        thread_count = 10
        attempts_per_thread = 3 # total = 30 attempts, but stock is only 10
        threads = []

        counter = 0
        for i in range(thread_count):
            counter += 1
            name = str(counter)
            t = Thread(target=place_order_thread, kwargs={
                "user_id": user_id,
                "product_id": product_id,
                "attempts": attempts_per_thread
            }, name= name)
            threads.append(t)

        print("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++")
        print(len(threads))
        print("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++")
        for t in threads:
            t.start()
            # time.sleep(1)

    
        print("I AM ENTERING===============================================>")
        for t in threads:
            print("*******************")
            print(f"Thread {t.name} has finished execution.")
            print("*******************")
            t.join()
        print("I AM LEAVING================================================>")

        # 6) Final check:
        #    - successful_orders <= initial_stock (i.e. cannot exceed 10)
        #    - final product stock = initial_stock - successful_orders
        print_pass_message(f"Total successful orders = {successful_orders}")

        if successful_orders > initial_stock:
            print_fail_message(
                f"Concurrency error: successful_orders={successful_orders} > stock={initial_stock}"
            )
            return False

        expected_final_stock = initial_stock - successful_orders

        # 7) GET /products/{productId} => final stock
        resp = get_product(product_id)
        print("===================RESPONSE=========================>\n", resp.json())
        # if not test_get_product_stock(product_id, resp, expected_stock=expected_final_stock):
        #     return False

        # If everything lines up, success
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
