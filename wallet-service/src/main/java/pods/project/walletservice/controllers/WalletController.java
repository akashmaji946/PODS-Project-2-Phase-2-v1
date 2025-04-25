package pods.project.walletservice.controllers;

import pods.project.walletservice.entities.Wallet;
import pods.project.walletservice.services.WalletService;
import pods.project.walletservice.CustomException.*;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;
import java.net.*;
import java.io.*;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/wallets")
public class WalletController {
	
	@Autowired
    private WalletService walletService;


	// GET /wallets/{userId} - Retrieve wallet by userId
    @GetMapping("/{userId}")
	@Transactional
    public ResponseEntity<Object> getUserById(@PathVariable int userId) {
        try{
			Wallet wallet = walletService.getByUserId(userId);
			return new ResponseEntity<>(wallet, HttpStatus.OK);
		}
		catch(WalletNotExistsException ex){
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
		}
		catch(Exception e){
			return new ResponseEntity<>("Error in getting wallet detail::" + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
    }

	// PUT /wallets/{userId} - Update the wallet balance if it exists else create a new wallet with zero balance
    @PutMapping("/{userId}")
    public ResponseEntity<Object> updateWalletBalance(@PathVariable Integer userId, @RequestBody Map<String, String> walletDetails) {
        try{
			Wallet wallet = walletService.updateWalletBalance(userId, walletDetails);
			return new ResponseEntity<>(wallet, HttpStatus.OK);
		}
		catch(InsufficientBalanceException ex){
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
		catch(BadRequestException ex){
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
		catch(Exception e){
			return new ResponseEntity<>("Error in updating wallet detail::" + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
    }

    // DELETE /wallets/{userId} - Delete the wallet associated with the userId
	@DeleteMapping("/{userId}")
	public ResponseEntity<Object> deleteUser(@PathVariable int userId) {
		try {
			// Attempt to delete wallet associated with the userId
			walletService.deleteWallet(userId);
			return new ResponseEntity<>("Wallet deleted successfully", HttpStatus.OK);
		} 
		catch(WalletNotExistsException ex){
			// Handle specific exception related to wallet not found
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
		}
		catch (Exception e) {
			// Catch any other generic exceptions
			return new ResponseEntity<>("Error in deleting wallet::" + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}


    // DELETE /wallets - Delete all wallets in Wallet services
    @DeleteMapping("")
    public ResponseEntity<Object> deleteAllWallets() {
		try {
			// Attempt to delete all wallets
			walletService.deleteAllWallets();
			return new ResponseEntity<>("All Wallets deleted successfully", HttpStatus.OK);
		}
		catch (Exception e) {
			// Catch any other generic exceptions
			return new ResponseEntity<>("Error in deleting all wallets::" + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
    }


}