import sys
import requests
import random
import time
from threading import Thread, Lock

from user import post_user
from wallet import put_wallet, get_wallet, test_get_wallet
from utils import check_response_status_code, print_fail_message, print_pass_message

WALLET_SERVICE_URL = "http://localhost:8082"
USER_COUNT = 100  # Number of users to test concurrency

# Global dictionary to track credited and debited amounts per user
user_balances = {}
lock = Lock()  # Ensures thread safety when updating global variables
credits = 0
debits = 0

def credit_thread_function(user_id, iterations=2):
    """
    Each thread credits a random amount (10..100) to the user's wallet.
    Updates global credited amount safely.
    """
    global user_balances
    global credits
    global debits
    for _ in range(iterations):
        amount = random.randint(1000, 10000)
        resp = requests.put(f"{WALLET_SERVICE_URL}/wallets/{user_id}",
                            json={"action": "credit", "amount": amount})
        if resp.status_code == 200:
            with lock:
                credits += 1
                user_balances[user_id]["credited"] += amount


def debit_thread_function(user_id, iterations=2):
    """
    Each thread debits a random amount (5..50) from the user's wallet.
    Updates global debited amount safely.
    """
    global user_balances
    global credits
    global debits
    for _ in range(iterations):
        amount = random.randint(500, 5000)
        resp = requests.put(f"{WALLET_SERVICE_URL}/wallets/{user_id}",
                            json={"action": "debit", "amount": amount})
        if resp.status_code == 200:
            with lock:
                debits += 1
                user_balances[user_id]["debited"] += amount


def main():
    try:
        global user_balances
        global credits
        global debits

        threads = []
        
        # Create multiple users and initialize their wallets
        for i in range(USER_COUNT):
            user_id = 40000 + i  # Unique user IDs
            resp = post_user(user_id, f"New User_{user_id}", f"brand.user{user_id}@csa.iisc.com")
            if not check_response_status_code(resp, 201):
                return False

            # Initial wallet balance
            initial_balance = 10000
            resp = put_wallet(user_id, "credit", initial_balance)
            if not check_response_status_code(resp, 200):
                return False

            # Track each user's transactions
            user_balances[user_id] = {"initial": initial_balance, "credited": 0, "debited": 0}

            # Create and start credit/debit threads
            t1 = Thread(target=credit_thread_function, kwargs={"user_id": user_id, "iterations": 123})
            t2 = Thread(target=debit_thread_function, kwargs={"user_id": user_id, "iterations": 123})

            threads.extend([t1, t2])
            t1.start()
            t2.start()

        # Wait for all threads to complete
        for t in threads:
            t.join()

        # Verify each user's final balance
        for user_id in user_balances.keys():
            final_expected_balance = (
                user_balances[user_id]["initial"]
                + user_balances[user_id]["credited"]
                - user_balances[user_id]["debited"]
            )

            resp = get_wallet(user_id)
            expected_balance = resp.json()["balance"]
            # print(f"User {user_id}: \nExpected Balance = {final_expected_balance}")
            # print(f"API Response Balance: {expected_balance}")
            # print_pass_message(expected_balance == final_expected_balance)
            assert expected_balance == final_expected_balance, "Mismatch in balance"
            if not test_get_wallet(user_id, resp, True, balance=final_expected_balance):
                return False

        # print(credits, debits)
        print("Total Credits:", credits)
        print("Total Debits:", debits)
        print_pass_message("Wallet concurrency test passed for all users.")
        return True

    except Exception as e:
        print_fail_message(f"Test crashed with exception: {e}")
        return False


if __name__ == "__main__":
    if main():
        sys.exit(0)
    else:
        sys.exit(1)
