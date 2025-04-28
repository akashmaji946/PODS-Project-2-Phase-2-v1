import requests  
import random  

# Base URLs of the services  
USER_SERVICE_URL = "http://localhost:8080"  
MARKETPLACE_SERVICE_URL = "http://localhost:8081"  
WALLET_SERVICE_URL = "http://localhost:8082"  

def main():  
    # Define users with their respective details  
    users = [  
        {"id": 1111, "name": "Alice Johnson", "email": "alice.johnson@example.com", "productId": 111},  
        {"id": 2222, "name": "Bob Smith", "email": "bob.smith@example.com", "productId": 112},  
        {"id": 3333, "name": "Charlie Brown", "email": "charlie.brown@example.com", "productId": 113},  
        {"id": 4444, "name": "Diana Evans", "email": "diana.evans@example.com", "productId": 114},  
        {"id": 5555, "name": "Ethan Harris", "email": "ethan.harris@example.com", "productId": 115},  
        {"id": 6666, "name": "Fiona Clark", "email": "fiona.clark@example.com", "productId": 116},  
        {"id": 7777, "name": "George Wilson", "email": "george.wilson@example.com", "productId": 117},  
        {"id": 8888, "name": "Hannah Martinez", "email": "hannah.martinez@example.com", "productId": 118},  
        {"id": 9999, "name": "Ian Taylor", "email": "ian.taylor@example.com", "productId": 119},  
        {"id": 1010, "name": "Julia Anderson", "email": "julia.anderson@example.com", "productId": 120}  
    ]  

    # Clean up previous users and wallets  
    delete_users() 
    delete_wallets() 

    # Iterate through each user and perform the test  
    for user in users:  
        qty = random.randint(1, 3)  
        print(f"Testing for User: {user['name']}")  
        test_order_flow(user["id"], user["name"], user["email"], user["productId"], qty)  
        print("-" * 50)  # Separator for clarity  

def create_user(id, name, email):  
    """Create a new user."""  
    new_user = {"id": id, "name": name, "email": email}  
    response = requests.post(f"{USER_SERVICE_URL}/users", json=new_user)  
    assert response.status_code == 201, "Failed to create user"  
    return response.json()  

def update_wallet(user_id, action, amount):  
    """Update the wallet balance."""  
    response = requests.put(f"{WALLET_SERVICE_URL}/wallets/{user_id}", json={"action": action, "amount": amount})  
    assert response.status_code == 200, "Failed to update wallet"  
    return response.json()  

def place_order(order_data):  
    """Place an order."""  
    response = requests.post(f"{MARKETPLACE_SERVICE_URL}/orders", json=order_data)  
    print(response.text, response.status_code)
    assert response.status_code == 201, "Failed to place order"  
    return response.json()  

def update_order_status(order_id, status):  
    """Update the order status."""  
    response = requests.put(f"{MARKETPLACE_SERVICE_URL}/orders/{order_id}", json={"order_id": order_id, "status": status})  
    print(response.text, response.status_code)
    assert response.status_code == 200, "Failed to update order status"  
    return response.json()  

def get_order(oid):  
    """Get a order for a user."""  
    response = requests.get(f"{MARKETPLACE_SERVICE_URL}/orders/{oid}")  
    assert response.status_code == 200, "Failed to fetch orders"  
    return response.json()  

def delete_users():  
    """Delete all users."""  
    response = requests.delete(f"{USER_SERVICE_URL}/users")  
    assert response.status_code == 200, "Failed to delete users" 

def delete_wallets():
    response = requests.delete(WALLET_SERVICE_URL + "/wallets")
    print("Wallets deleted") 
    return response

def delete_order(oid):
    response = requests.delete(MARKETPLACE_SERVICE_URL + "/orders/" + str(oid))
    print("Orders cancelled for user", oid)
    return response 

def test_order_flow(user_id, name, email, product_id, qty):  
    try:  
        # Create a new user  
        create_user(user_id, name, email)  
        print("User Created:", user_id, name, email)
        # Add money to the user's wallet  
        update_wallet(user_id, "credit", 1000000)  
        print("Wallet Updated:", user_id, "credited with 1000000")
        # Place an order  
        order_data = {"items": [{"product_id": product_id, "quantity": qty}], "user_id": user_id}  
        placed_order = place_order(order_data)  
        print("Placed Order: {}\n".format(placed_order["order_id"]), placed_order)  

        # Verify the order is in "placed" state  
        # orders = get_orders(user_id) 
        # print("All Orders:\n", orders)

        # assert len(orders) > 0, "No orders found for the user" 

        # for order in orders:
        #     print(order["order_id"], "=>", order["status"])

        # print(orders[0])
        # Update the order status to "delivered"
     
        order_id = placed_order["order_id"]  # Assume we update the first order  
        updated_order = update_order_status(order_id, "DELIVERED")  
        print("Updated Order:", updated_order)  

        # Verify the order is in "delivered" state
        oid = updated_order["order_id"]  
        updated_orders = get_order(oid)  
        # for order in updated_orders:  
        if updated_orders["order_id"] == order_id:  
            assert updated_orders["status"] == "DELIVERED", f"Order {updated_orders['id']} is not in 'delivered' state"

        orders_deleted = delete_order(oid) 
        print(orders_deleted.text)

        # orders = get_orders(user_id) 
        # print("All Orders:\n", orders)
        # for order in orders:
        #     print(order["order_id"], "=>", order["status"])


        print("Test Passed")  
    except Exception as e:  
        print(f"Some Exception Occurred: {e}")  

if __name__ == "__main__":  
    main()