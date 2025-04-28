import requests  

userServiceURL = "http://localhost:8080"  
marketplaceServiceURL = "http://localhost:8081"  
walletServiceURL = "http://localhost:8082"  

def main():  
    # Define three users with their respective details  
    users = [  
        {"id": 1001, "name": "User1", "email": "user1@example.com", "productId": 107},  
        {"id": 1002, "name": "User2", "email": "user2@example.com", "productId": 108},  
        {"id": 1003, "name": "User3", "email": "user3@example.com", "productId": 109}  
    ]  

    # Set a fixed wallet amount for all users  
    walletAmount = 1000000

    # Clean up previous users and wallets  
    delete_users()    

    # Iterate through each user and perform the test  
    for user in users:  
        print(f"Testing for User: {user['name']}")  
        add_money_and_check_detail(user["id"], user["name"], user["email"], user["productId"], walletAmount)  
        print("-" * 50)  # Separator for clarity  

def create_user(id, name, email):  
    new_user = {"id": id, "name": name, "email": email}  
    response = requests.post(userServiceURL + "/users", json=new_user)  
    return response  

def get_wallet(user_id):  
    response = requests.get(walletServiceURL + f"/wallets/{user_id}")  
    return response  

def update_wallet(user_id, action, amount):  
    response = requests.put(walletServiceURL + f"/wallets/{user_id}", json={"action": action, "amount": amount})  
    return response  

def get_product_details(product_id):  
    response = requests.get(marketplaceServiceURL + f"/products/{product_id}")  
    return response  

def cancel_order(order_id):  
    response = requests.delete(marketplaceServiceURL + f"/order/{order_id}")  
    return response  

def delete_users():  
    requests.delete(userServiceURL + "/users")  

def add_money_and_check_detail(id, name, email, productId, walletAmount):  
    try:  
    
        # Create a new user  
        new_user = create_user(id, name, email)  
        new_userid = new_user.json()['id']  

        # old_wallet_balance = get_wallet(new_userid).json()['balance']
        # print("Old wallet balance: ", old_wallet_balance)

        # Add money to the user's wallet  
        update_wallet(new_userid, "credit", walletAmount)  

        # Get product details before placing the order  
        product_details_before_ordering = get_product_details(productId)  

        # Get the wallet balance before placing the order  
        old_wallet_balance = get_wallet(new_userid).json()['balance']  


        # Place an order in the marketplace  
        new_order = {"items": [{"product_id": productId, "quantity": 2}], "user_id": new_userid}  
        res = requests.post(marketplaceServiceURL + "/orders", json=new_order)  
        print(res, res.text)

        # Get product details after placing the order  
        product_details_after_ordering = get_product_details(productId)  

        # Get the wallet balance after placing the order  
        new_wallet_balance = get_wallet(new_userid).json()['balance']  

        print("Balances: ",old_wallet_balance, new_wallet_balance)
        print(old_wallet_balance - new_wallet_balance == (product_details_after_ordering.json()['price'] * 2) * 0.9)
        print((product_details_before_ordering.json()['stock_quantity'] - product_details_after_ordering.json()['stock_quantity'] == 2))
        # Check for the correct balance amount in the wallet and the correct quantity  
        
        print("Product details before ordering: ", product_details_before_ordering.json())
        print("Product details after ordering: ", product_details_after_ordering.json())
        
        if (old_wallet_balance - new_wallet_balance == (product_details_after_ordering.json()['price'] * 2) * 0.9) and \
           (product_details_before_ordering.json()['stock_quantity'] - product_details_after_ordering.json()['stock_quantity'] == 2):  
            print("Test Passed")  
        else:  
            print("Test Failed")  
    except Exception as e:  
        print(f"Some Exception Occurred: {e}")  

if __name__ == "__main__":  
    main()