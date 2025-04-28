import requests  

userServiceURL = "http://localhost:8080"  
marketplaceServiceURL = "http://localhost:8081"  
walletServiceURL = "http://localhost:8082"  

def main():  
    # Define three users with their respective details  
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

    # Iterate through each user and perform the test  
    for user in users:  
        print(f"Testing for User: {user['name']}")  
        add_money_and_place_order(user["id"], user["name"], user["email"], user["productId"])  
        print("-" * 50)  # Separator for clarity  

def create_user(id, name, email):  
    new_user = {"id": id, "name": name, "email": email}  
    response = requests.post(userServiceURL + "/users", json=new_user)  
    return response  

def create_wallet(user_id):  
    requests.put(walletServiceURL + f"/wallets/{user_id}", json={"action": "credit", "amount": 0})  

def get_wallet(user_id):  
    response = requests.get(walletServiceURL + f"/wallets/{user_id}")  
    return response  

def update_wallet(user_id, action, amount):  
    response = requests.put(walletServiceURL + f"/wallets/{user_id}", json={"action": action, "amount": amount})  
    return response  

def get_product_details(product_id):  
    response = requests.get(marketplaceServiceURL + f"/products/{product_id}")  
    return response  

def delete_order(oid):  
    response = requests.delete(marketplaceServiceURL + f"/orders/{oid}")  
    return response  

def delete_users():  
    requests.delete(userServiceURL + "/users")  

def add_money_and_place_order(id, name, email, productId):  
    try:   

        # Create a new user  
        new_user = create_user(id, name, email)  
        new_userid = new_user.json()['id']  

        # Add money to the user's wallet  
        update_wallet(new_userid, "credit", 1000000)  

        # Get product details before placing the order  
        product_details_before_ordering = get_product_details(productId)  

        # Get the wallet balance before placing the order  
        old_wallet_balance = get_wallet(new_userid).json()['balance']  

        # Place an order in the marketplace  
        new_order = {"items": [{"product_id": productId, "quantity": 3}], "user_id": new_userid}  
        res = requests.post(marketplaceServiceURL + "/orders", json=new_order)  
        order_id = res.json()['order_id']
        # Delete the order (simulate cancellation)  
        delete_order(order_id)  

        # Get product details after deleting the order  
        product_details_after_ordering = get_product_details(productId)
        # print(product_details_after_ordering)  

        # Check if the refund and stock quantity are correct after cancellation  
        if (product_details_after_ordering.json()['stock_quantity'] == product_details_before_ordering.json()['stock_quantity']) and \
           (old_wallet_balance == get_wallet(new_userid).json()['balance']):  
            print("Test Passed")  
        else:  
            print("Test Failed")  
    except Exception as e:  
        print(f"Some Exception Occurred: {e}")  

if __name__ == "__main__":  
    main()