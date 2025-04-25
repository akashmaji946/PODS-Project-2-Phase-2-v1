package pods.project.walletservice.services;

import pods.project.walletservice.entities.Wallet;
import pods.project.walletservice.repositories.WalletRepository;
import pods.project.walletservice.CustomException.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.data.jpa.repository.JpaRepository;



import java.util.*;

@Service
public class WalletService {

    @Autowired
    private WalletRepository walletRepository;


    // Method to check if wallet exists with the given userId
    public Wallet getByUserId(int userId) {
        // Try to find the wallet by userId
        List<Wallet> wallet = walletRepository.findByUserId(userId);
        if (wallet.isEmpty()) {
            throw new WalletNotExistsException("Wallet with id " + userId + " does not exist.");
        }
        return wallet.get(0);
    }

    // Update the wallet balance of the user with the given userId
    @Transactional
    public Wallet updateWalletBalance(Integer user_id, Map<String, String> walletDetails) {
        // Try to find the user by userId
        Wallet existingWallet = null, newWallet = null;
        try{
            existingWallet = this.getByUserId(user_id);
        }
        catch(WalletNotExistsException ex){
            newWallet =  new Wallet();
            newWallet.setUserId(user_id);
            newWallet.setBalance(0);
            existingWallet = walletRepository.save(newWallet);
        }
        boolean validPayload =  walletDetails != null && existingWallet!= null;
        validPayload = validPayload && walletDetails.get("amount") != null && walletDetails.get("action") != null && (walletDetails.get("action").equals("credit") || walletDetails.get("action").equals("debit"));
        validPayload = validPayload && !walletDetails.get("amount").isEmpty() && !walletDetails.get("action").isEmpty();
        if (!validPayload) {
            throw new BadRequestException("Invalid payload");
        }

        Integer newBalance = null;
        try{
            Integer oldBalance = existingWallet.getBalance();
            newBalance = oldBalance;
            if(walletDetails.get("action").equals("credit")) {
                newBalance += Integer.parseInt(walletDetails.get("amount"));
            }else{
                newBalance -= Integer.parseInt(walletDetails.get("amount"));
            }
            if(newBalance < 0){
                throw new InsufficientBalanceException("Insufficient balance");
            }
        }
        catch(InsufficientBalanceException e) {
            throw e;
        }
        catch (Exception e) {
            throw new BadRequestException("Invalid payload");
        }
        Wallet wallet_ = new Wallet();
        wallet_.setBalance(newBalance);
        wallet_.setUserId(user_id); /* this user must exist */
        walletRepository.save(wallet_);
        return wallet_;
    }

    // Delete user by userId
    @Transactional
    public void deleteWallet(int userId) {
        // Check if the Wallet exists
        try{
            Wallet existingWallet = this.getByUserId(userId);
        }
        catch(WalletNotExistsException ex){
            throw ex;
        }
        // Delete the wallet in the Wallet Service
        walletRepository.deleteById(userId);
    }

    // Delete all user
    public void deleteAllWallets() {
        // Delete all the wallets in the Wallet Service
        walletRepository.deleteAll();
    }
}