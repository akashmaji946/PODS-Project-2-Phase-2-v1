import requests
from marketplace import post_order, delete_order, get_product
from user import post_user
from wallet import put_wallet
from utils import check_response_status_code, print_fail_message, print_pass_message

MARKETPLACE_SERVICE_URL = "http://localhost:8081"

def main():
    try:
        # Step 1: Create a user
        user_id = 989898
        resp = post_user(user_id, "Test1  User", "test1user@example.com")
        if not check_response_status_code(resp, 201):
            return False

        # Step 2: Add funds to the user's wallet
        resp = put_wallet(user_id, "credit", 100000)
        if not check_response_status_code(resp, 200):
            return False

        # Step 3: Get initial product info
        product_id = 101  # Example product ID
        resp = get_product(product_id)
        if not check_response_status_code(resp, 200):
            return False
        initial_stock = resp.json()["stock_quantity"]
        print(f"Initial stock for product {product_id}: {initial_stock}")

        # Step 4: Place an order
        quantity = 2
        resp = post_order(user_id, [{"product_id": product_id, "quantity": quantity}])
        if not check_response_status_code(resp, 201):
            return False
        order_id = resp.json().get("order_id")
        print(f"Order placed successfully. Order ID: {order_id}")

        # Step 5: Get product info after placing the order
        resp = get_product(product_id)
        if not check_response_status_code(resp, 200):
            return False
        stock_after_order = resp.json()["stock_quantity"]
        print(f"Stock after placing order for product {product_id}: {stock_after_order}")

        # Step 6: Cancel the order
        resp = delete_order(order_id)
        if not check_response_status_code(resp, 200):
            return False
        print(f"Order {order_id} canceled successfully.")

        # Step 7: Get product info after canceling the order
        resp = get_product(product_id)
        if not check_response_status_code(resp, 200):
            return False
        stock_after_cancel = resp.json()["stock_quantity"]
        print(f"Stock after canceling order for product {product_id}: {stock_after_cancel}")

        # Step 8: Verify stock consistency
        expected_stock = initial_stock
        if stock_after_cancel == expected_stock:
            print_pass_message(f"Stock consistency verified for product {product_id}.")
        else:
            print_fail_message(f"Stock mismatch for product {product_id}: expected {expected_stock}, got {stock_after_cancel}")
            return False

        return True
    except Exception as e:
        print_fail_message(f"Test crashed: {e}")
        return False

if __name__ == "__main__":
    if main():
        print("Test completed successfully.")
    else:
        print("Test failed.")