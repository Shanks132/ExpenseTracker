package shanks.personal.demo.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import shanks.personal.demo.dto.CategoryTotal;
import shanks.personal.demo.entity.Transaction;
import shanks.personal.demo.entity.TransactionType;
import shanks.personal.demo.entity.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Transactional
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByUserId(Long userId);

    //    List<Transaction> findByUserIdOrderByTransactionDateDesc(Long userId);
    Page<Transaction> findByUserIdOrderByTransactionDateDesc(Long userId, Pageable pageable);

    void deleteByUserId(Long userId);

    @Query("""

            SELECT SUM(t.amount) FROM Transaction t
    WHERE t.user.id = :userId AND t.type = :type
      AND t.transactionDate >= :start AND t.transactionDate < :end
    """)
BigDecimal sumByType(@Param("userId") Long userId, @Param("type") TransactionType type,
                     @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

@Query("""
    SELECT new shanks.personal.demo.dto.CategoryTotal(COALESCE(t.category, 'Uncategorized'), SUM(t.amount))
    FROM Transaction t
    WHERE t.user.id = :userId AND t.type = shanks.personal.demo.entity.TransactionType.EXPENSE
      AND t.transactionDate >= :start AND t.transactionDate < :end
    GROUP BY COALESCE(t.category, 'Uncategorized')
    ORDER BY SUM(t.amount) DESC
    """)
List<CategoryTotal> expenseByCategory(@Param("userId") Long userId,
                                      @Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);
}