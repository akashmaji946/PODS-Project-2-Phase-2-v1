import requests  

userServiceURL = "http://localhost:8080"  
marketplaceServiceURL = "http://localhost:8081"  
walletServiceURL = "http://localhost:8082"  

def main():  
    # Delete all users and wallets (cleanup)  
    delete_users()
    delete_wallets()   

    # Define the three users  
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

    # Create users and their wallets  
    for user in users:  
        user_id = user["id"]  
        name = user["name"]  
        email = user["email"]  

        print(f"=> create_user({name}, {email})")  
        response_create_user = create_user(user_id, name, email)  
        print(f"<= create_user() response: {response_create_user.json()}")  

        if test_create_user(name, email, response_create_user):  
            print("create_user() Passed\n")  

        print(f"=> create_wallet({user_id})")  
        create_wallet(user_id)  
        response_get_wallet = get_wallet(user_id)  
        print(f"<= get_wallet() response: {response_get_wallet.json()}")  

        if test_get_wallet(user_id, response_get_wallet):
            print("get_wallet() Passed\n")  

        # Update wallets with credits or debits  
        wallet = response_get_wallet.json()  
        old_balance = wallet['balance']  
        action = "credit"  
        amount = 100000  

        print(f"=> update_wallet({user_id}, {action}, {amount})")  
        response_update_wallet = update_wallet(user_id, action, amount)  
        print(f"<= update_wallet() response: {response_update_wallet.json()}")  

        if test_update_wallet(user_id, action, amount, old_balance, response_update_wallet):  
            print("update_wallet() Passed.\n")

        response_get_wallet = get_wallet(user_id)  
        print(f"<= get_wallet() response: {response_get_wallet.json()}")  

        if test_get_wallet(user_id, response_get_wallet):  
            print("get_wallet() Passed\n")  

        # Update wallets with credits or debits  
        wallet = response_get_wallet.json()  
        old_balance = wallet['balance']  
        action = "debit"  
        amount = 100000  

        print(f"=> update_wallet({user_id}, {action}, {amount})")  
        response_update_wallet = update_wallet(user_id, action, amount)  
        print(f"<= update_wallet() response: {response_update_wallet.json()}")  

        if test_update_wallet(user_id, action, amount, old_balance, response_update_wallet):  
            print("update_wallet() Passed.\n") 

        print("-"*50)

    # delete_users()
    # delete_wallets() 

def create_user(user_id, name, email):  
    new_user = {"id": user_id, "name": name, "email": email}  
    response = requests.post(userServiceURL + "/users", json=new_user)  
    return response  

def test_create_user(name, email, response):  
    new_user = response.json()  

    # Check if 'id' field exists in the response  
    if 'id' not in new_user:  
        print("create_user() Failed: 'id' field not present in response.")  
        return False  
    # Check the type of 'id' as int  
    elif not isinstance(new_user['id'], int):  
        print("create_user() Failed: 'id' field not an integer value.")  
        return False  

    # Check if 'name' field exists in the response  
    if 'name' not in new_user:  
        print("create_user() Failed: 'name' field not present in response.")  
        return False  

    # Check if 'email' field exists in the response  
    if 'email' not in new_user:  
        print("create_user() Failed: 'email' field not present in response.")  
        return False  

    # Check the number of fields returned in the response  
    if len(new_user) != 4:  
        print("create_user() Failed: Incorrect response format.")  
        return False  

    # Check the status code  
    if response.status_code != 201:  
        print(f"create_user() Failed: HTTP 201 expected, got {response.status_code}")  
        return False  

    return True  

def delete_users():  
    requests.delete(userServiceURL + "/users")
    print("Deleting users...")  

def create_wallet(user_id):  
    requests.put(walletServiceURL + f"/wallets/{user_id}", json={"action": "credit", "amount": 0})  

def get_wallet(user_id):  
    response = requests.get(walletServiceURL + f"/wallets/{user_id}")  
    return response 

def delete_wallets():
    response = requests.delete(walletServiceURL + "/wallets")
    print("Wallets deleted") 
    return response


def test_get_wallet(user_id, response):  
    user_res = requests.get(userServiceURL + f"/users/{user_id}")  

    # Check for the correct status code  
    if user_res.status_code != 404 and response.status_code == 404:  
        return False  

    payload = response.json()  

    # Check if 'user_id' field exists in response  
    if 'user_id' not in payload:  
        print("get_wallet() Failed: 'user_id' field not present in response.")  
        return False  

    # Check if 'user_id' field is int in response  
    if not isinstance(payload['user_id'], int):  
        print("get_wallet() Failed: 'user_id' field not an integer value.")  
        return False  

    if 'balance' not in payload:  
        print("get_wallet() Failed: 'balance' field not present in response.")  
        return False  

    if not isinstance(payload['balance'], int):  
        print("get_wallet() Failed: 'balance' field not an integer value.")  
        return False  

    if len(payload) != 2:  
        print("get_wallet() Failed: Incorrect response format.")  
        return False  

    if response.status_code != 200:  
        print(f"get_wallet() Failed: HTTP 200 expected, got {response.status_code}")  
        return False  

    return True  

def update_wallet(user_id, action, amount):  
    response = requests.put(walletServiceURL + f"/wallets/{user_id}", json={"action": action, "amount": amount})  
    return response  

def test_update_wallet(user_id, action, amount, old_balance, response):  
    # Check if negative balance is allowed or not  
    if action == 'debit' and old_balance < amount and response.status_code != 400:  
        print(f"update_wallet() Failed: insufficient balance, expected HTTP 400, got {response.status_code}.")  
        return False  

    payload = response.json()  

    if 'user_id' not in payload:  
        print("update_wallet() Failed: 'user_id' field not present in response.")  
        return False
    
main()