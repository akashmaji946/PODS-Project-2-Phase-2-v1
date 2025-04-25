package pods.project.accountservice.services;

import pods.project.accountservice.entities.Account;
import pods.project.accountservice.repositories.AccountRepository;
import pods.project.accountservice.CustomException.AccountNotExistsException;
import pods.project.accountservice.CustomException.EmailAlreadyExistsException;
import pods.project.accountservice.CustomException.FailedToCancelOrdersException;
import pods.project.accountservice.CustomException.FailedToRemoveWalletException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.web.client.HttpClientErrorException;



import java.util.List;

@Service
public class AccountService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${service2.delete.userid.marketplace-url}")
    private String userMarketplaceUrl;

    @Value("${service2.delete.all.marketplace-url}")
    private String allMarketplaceUrl;

    @Value("${service3.delete.userid.wallet-url}")
    private String userWalletUrl;

    @Value("${service3.delete.all.wallet-url}")
    private String allWalletUrl;


    public List<Account> getAllAccount() {
        return accountRepository.findAll();
    }

    // Method to check if an account exists with the given userId
    public Account getUserById(int userId) {
        // Try to find the user by userId
        List<Account> account = accountRepository.findById(userId);
        if (account.isEmpty()) {
            throw new AccountNotExistsException("User with id " + userId + " does not exist.");
        }
        return account.get(0);
    }

    // Method to check if an account exists with the given email
    public List<Account> findByEmail(String email) {
        return accountRepository.findByEmail(email);
    }

    // Update the account (set discountAvailed to true or false)
    public Account updateAccount(Account accountDetails) {
        // Try to find the user by userId
        Account existingAccount = null;
        try{
            existingAccount = this.getUserById(accountDetails.getId());
        }
        catch(AccountNotExistsException ex){
            throw ex;
        }

        // Update the discount_availed field
        existingAccount.setDiscountAvailed(accountDetails.getDiscountAvailed());

        // Save the updated account
        return accountRepository.save(existingAccount);
    }
    
    // Method to create a new account
    @Transactional
    public Account createAccount(Account account) {
        // Check if the email already exists in the database
        List<Account> existingAccount = accountRepository.findByEmail(account.getEmail());

        // If the email already exists, throw an exception
        if (!existingAccount.isEmpty()) {
            throw new EmailAlreadyExistsException("Email " + account.getEmail() + " already exists.");
        }

        // If email does not exist, create the new account and return result
        return accountRepository.save(account);
    }

    // Delete user by userId
    @Transactional
    public void deleteUser(int userId) {
        // Step 1: Check if the user exists
        try{
            Account existingAccount = this.getUserById(userId);
        }
        catch(AccountNotExistsException ex){
            throw ex;
        }

        // Step 2: Call DELETE /marketplace/users/{userId} to cancel the user's orders
        boolean ordersDeleted = this.cancelUserOrders(userId);

        if (!ordersDeleted) {
          throw new FailedToCancelOrdersException("Failed to cancel user orders of user with id " + userId);
        }

        // Step 3: Call DELETE /wallets/{userId} to remove the user's wallet
        boolean walletDeleted = this.removeUserWallet(userId);

        if (!walletDeleted) {
            throw new FailedToRemoveWalletException("Failed to remove user wallet of user with id " + userId);
        }

        // Step 4: Delete the user in the Account Service
        accountRepository.deleteById(userId);
    }

    // Delete all user
    @Transactional
    public void deleteAllUser() {

        // Step 1: Call DELETE /marketplace/users/ to cancel the users orders
        boolean ordersDeleted = this.cancelAllUsersOrders();

        if (!ordersDeleted) {
           throw new FailedToCancelOrdersException("Failed to cancel users orders");
        }

        // Step 2: Call DELETE /wallets/ to remove the users wallet
        boolean walletDeleted = this.removeAllUsersWallets();

        if (!walletDeleted) {
            throw new FailedToRemoveWalletException("Failed to remove users wallet");
        }

        // Step 3: Delete all the users in the Account Service
        accountRepository.deleteAll();
    }

    public boolean cancelAllUsersOrders(){
        try {
            String url = allMarketplaceUrl;

            // Make the API call
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE, null, String.class);

            // Handle specific HTTP status codes
            if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.NOT_FOUND) {
                // Success (200), Not found 404 
                return true;
            } 
            else {
                // Handle any other response codes as a 500 internal error
                return false;
            }
        }
        catch (HttpClientErrorException.NotFound ex) {
            // Handle 404 Not Found exception separately
            return true; 
        }
        catch (Exception e) {
            // Catch any other exceptions
            return false;
        }
    }


    public boolean removeAllUsersWallets(){
        try {
            String url = allWalletUrl;

            // Make the API call
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE, null, String.class);

            // Handle specific HTTP status codes
            if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.NOT_FOUND) {
                // Success (200), Not found 404 
                return true;
            } 
            else {
                // Handle any other response codes as a 500 internal error
                return false;
            }
        }
        catch (HttpClientErrorException.NotFound ex) {
            // Handle 404 Not Found exception separately
            return true; 
        }
        catch (Exception e) {
            // Catch any other exceptions
            return false;
        }
    }

    public boolean cancelUserOrders(int userId){
        try {
            String url = userMarketplaceUrl.replace("{userId}", String.valueOf(userId)); // Replace the placeholder {userId} with actual userId

            // Make the API call
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE, null, String.class);

            // Handle specific HTTP status codes
            if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.NOT_FOUND) {
                // Success (200) , Not found 404 
                return true;
            } 
            else {
                // Handle any other response codes as a 500 internal error
                return false;
            }
        }
        catch (HttpClientErrorException.NotFound ex) {
            // Handle 404 Not Found exception separately
            return true; 
        }
        catch (Exception e) {
            // Catch any other exceptions
            return false;
        }
    }

    public boolean removeUserWallet(int userId){
        try {
            String url = userWalletUrl.replace("{userId}", String.valueOf(userId)); // Replace the placeholder {userId} with actual userId
            
            // Make the API call
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE, null, String.class);

            // Handle specific HTTP status codes
            if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.NOT_FOUND) {
                // Success (200), Not found 404
                return true;
            } 
            else {
                // Handle any other response codes as a 500 internal error
                return false;
            }
        }
        catch (HttpClientErrorException.NotFound ex) {
            // Handle 404 Not Found exception separately
            return true; 
        }
        catch (Exception e) {
            // Catch any other exceptions
            return false;
        }
    }

}