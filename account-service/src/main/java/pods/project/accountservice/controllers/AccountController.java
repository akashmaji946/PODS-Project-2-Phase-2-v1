package pods.project.accountservice.controllers;

import pods.project.accountservice.entities.Account;
import pods.project.accountservice.services.AccountService;
import pods.project.accountservice.CustomException.*;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import java.net.*;
import java.io.*;
import java.util.*;

@RestController
@RequestMapping("/users")
public class AccountController {
	
	@Autowired
    private AccountService accountService;

	// POST /users/ - Create a new user
    @PostMapping("")
    public ResponseEntity<Object> createAccount(@RequestBody Account account) {
        try{
			Account newAccount = accountService.createAccount(account);
			return new ResponseEntity<>(newAccount, HttpStatus.CREATED);
		}
		catch(EmailAlreadyExistsException ex){
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
		catch(Exception e){
			return new ResponseEntity<>("Error in creating account" + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	// GET /users/{userId} - Retrieve user by userId
    @GetMapping("/{userId}")
    @Transactional
    public ResponseEntity<Object> getUserById(@PathVariable int userId) {
        try{
			Account account = accountService.getUserById(userId);
			return new ResponseEntity<>(account, HttpStatus.OK);
		}
		catch(AccountNotExistsException ex){
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
		}
		catch(Exception e){
			return new ResponseEntity<>("Error in getting account detail", HttpStatus.INTERNAL_SERVER_ERROR);
		}
    }

	// PUT /users/ - Update the discount_availed field for the user
    @PutMapping("")
    @Transactional
    public ResponseEntity<Object> updateDiscountAvailed(@RequestBody Account accountDetails) {
        try{
			Account account = accountService.updateAccount(accountDetails);
			return new ResponseEntity<>(account, HttpStatus.OK);
		}
		catch(AccountNotExistsException ex){
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
		}
		catch(Exception e){
			return new ResponseEntity<>("Error in updating account detail", HttpStatus.INTERNAL_SERVER_ERROR);
		}
    }

    // DELETE /users/{userId} - Delete the user and their associated data
	@DeleteMapping("/{userId}")
	public ResponseEntity<Object> deleteUser(@PathVariable int userId) {
		try {
			// Attempt to delete user and their associated data
			accountService.deleteUser(userId);
			return new ResponseEntity<>("User deleted successfully", HttpStatus.OK);
		} 
		catch(AccountNotExistsException ex){
			// Handle specific exception related to user not found
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
		}
		catch (FailedToCancelOrdersException ex) {
			// Handle specific exception related to failing to cancel user orders
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		} 
		catch (FailedToRemoveWalletException ex) {
			// Handle specific exception related to failing to remove user wallet
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		} 
		catch (Exception e) {
			// Catch any other generic exceptions
			return new ResponseEntity<>("Error in deleting account", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}


    // DELETE /users - Delete all users and reset external services
    @DeleteMapping("")
    public ResponseEntity<Object> deleteAllUsers() {
		try {
			// Attempt to delete all user and their associated data
			accountService.deleteAllUser();
			return new ResponseEntity<>("All User deleted successfully", HttpStatus.OK);
		}
		catch (FailedToCancelOrdersException ex) {
			// Handle specific exception related to failing to cancel users orders
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		} 
		catch (FailedToRemoveWalletException ex) {
			// Handle specific exception related to failing to remove users wallet
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		} 
		catch (Exception e) {
			// Catch any other generic exceptions
			return new ResponseEntity<>("Error in deleting all user account", HttpStatus.INTERNAL_SERVER_ERROR);
		}
    }


}