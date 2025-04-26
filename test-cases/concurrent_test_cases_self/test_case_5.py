import sys
import random
import time
import requests
from threading import Thread
from user import post_user
from wallet import put_wallet
from marketplace import (
    post_order, delete_order, get_product, test_post_order, test_get_product_stock
)
from utils import check_response_status_code, print_fail_message, print_pass_message

MARKETPLACE_SERVICE_URL = "http://localhost:8081"

import threading

placed_orders = {}
successful_orders = {}
deleted_orders = {}
lock = threading.Lock()

def place_order_thread(user_id, product_id, attempts=2):
    global successful_orders
    global placed_orders
    global deleted_orders
    for _ in range(attempts):
        with lock:
            quantity = random.randint(1, 2)
            resp = post_order(user_id, [{"product_id": product_id, "quantity": quantity}])
            
            if resp.status_code == 201:
                order_id = resp.json().get("order_id")
                if order_id:
                    successful_orders[order_id] = {"product_id": product_id, "quantity": quantity}
                    placed_orders[order_id] = {"product_id": product_id, "quantity": quantity}

def delete_order_thread():
    global successful_orders
    global placed_orders
    global deleted_orders
    while True:
        with lock:  # Ensuring thread-safe access
            if not successful_orders:
                break
            order_id, order_info = successful_orders.popitem()
        
            resp = delete_order(order_id)
        
            if resp.status_code == 200:
                deleted_orders[order_id] = order_info

def populate_products():
    api_url = "http://localhost:8081/products/populate"
    response = requests.get(api_url)
    
    if response.status_code == 200:
        print("Products populated successfully.")
        return response.text
    else:
        print(f"Failed to populate products: {response.status_code}, {response.text}")
        return None


def main():
    try:
        user_id = 1219
        resp = post_user(user_id, "PODS User", "pods@csa.in")
        if not check_response_status_code(resp, 201):
            return False

        resp = put_wallet(user_id, "credit", 12345678)
        if not check_response_status_code(resp, 200):
            return False

        # repopulated
        # populate_products()
        product_ids = [ 102, 103, 104, 105, 106, 107, 108, 109, 110]
        initial_stock = { 102: 8, 103: 15, 104: 20, 105: 12, 106: 5, 107: 25, 108: 5, 109: 10, 110: 7}

        # product_ids = [ 109, 110]
        # initial_stock = { 109: 10, 110: 7}

        global successful_orders, deleted_orders
        successful_orders.clear()
        deleted_orders.clear()

        for product_id in product_ids:
            resp = get_product(product_id)
            if resp.status_code == 200:
                pass

        order_threads = []
        delete_threads = []
        all_threads = []
        for product_id in product_ids:
            for _ in range(3):
                t = Thread(target=place_order_thread, args=(user_id, product_id))
                order_threads.append(t)
                all_threads.append(t)
                # t.start()
        
        # for t in order_threads:
        #     t.join()

        for _ in range(3):
            t = Thread(target=delete_order_thread)
            delete_threads.append(t)
            all_threads.append(t)
            # t.start()

        # for t in delete_threads:
        #     t.join()

        for t in all_threads:
            t.start()
            # time.sleep(0)  # Adding a small delay to stagger the thread starts
        
        for t in all_threads:
            t.join()

        # print("HERE-1")

        restored_stock = {}
        for order_info in deleted_orders.values():
            product_id = order_info["product_id"]
            quantity = order_info["quantity"]
            restored_stock[product_id] = restored_stock.get(product_id, 0) + quantity

        # print("HERE-2")

        for product_id in product_ids:
            resp = get_product(product_id)
            expected_stock = resp.json()["stock_quantity"]

            print("Initial, Placed, Cancelled:")
            print(initial_stock[product_id], ",", sum(o["quantity"] for o in placed_orders.values() if o["product_id"] == product_id), "," ,restored_stock.get(product_id, 0))

            final_expected_stock = initial_stock[product_id] - sum(o["quantity"] for o in placed_orders.values() if o["product_id"] == product_id) + restored_stock.get(product_id, 0)
            # print("HERE-3")
            if expected_stock != final_expected_stock:
                print_fail_message(f"Stock mismatch for product {product_id}: expected {final_expected_stock}, got {expected_stock}")
                return False
            print_pass_message(f"Stock MATCH for product {product_id}: expected {final_expected_stock}, got {expected_stock}")

        print_pass_message("Marketplace concurrency test passed.")
        
        return True
    except Exception as e:
        print_fail_message(f"Test crashed: {e}")
        return False
    
    dele

if __name__ == "__main__":
    if main():
        sys.exit(0)
    else:
        sys.exit(1)
