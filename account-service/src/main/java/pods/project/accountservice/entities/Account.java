package pods.project.accountservice.entities;

//import javax.persistence.*;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonProperty;


@Entity
public class Account {
    @Id
    // @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    private String name;

    @Column(unique = true)
    private String email;
    
    @Column(nullable = false)  // Ensures discount_availed is not null, but defaults to false
    private Boolean discountAvailed = false;

    // Getter and Setter for id
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    // Getter and Setter for name
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    // Getter and Setter for email
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    // Getter and Setter for discountAvailed
    @JsonProperty("discount_availed")
    public Boolean getDiscountAvailed() {
        return discountAvailed;
    }

    public void setDiscountAvailed(Boolean discountAvailed) {
        this.discountAvailed = discountAvailed;
    }
}