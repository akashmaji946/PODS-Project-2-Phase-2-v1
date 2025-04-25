package pods.project.walletservice.entities;
import com.fasterxml.jackson.annotation.JsonProperty;

//import javax.persistence.*;
import jakarta.persistence.*;


@Entity
public class Wallet {
    @Id
    @Column(name = "user_id")
    Integer user_id;

    @Column(name = "balance", nullable = false)
    Integer balance;

    // Getter for user_id
    @JsonProperty("user_id")
    public Integer getUserId() {
        return user_id;
    }

    // Setter for user_id
    public void setUserId(Integer user_id) {
        this.user_id = user_id;
    }

    // Getter for balance
    public Integer getBalance() {
        return balance;
    }

    // Setter for balance
    public void setBalance(Integer balance) {
        this.balance = balance;
    }
}