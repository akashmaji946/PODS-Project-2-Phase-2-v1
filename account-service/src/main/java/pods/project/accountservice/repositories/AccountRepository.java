package pods.project.accountservice.repositories;

import pods.project.accountservice.entities.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "SELECT a FROM Account a WHERE a.email = :email")
    List<Account> findByEmail(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "SELECT a FROM Account a WHERE a.id = :id")
    List<Account> findById(@Param("id") int id);
}