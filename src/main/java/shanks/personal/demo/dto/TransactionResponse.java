package shanks.personal.demo.dto;

import shanks.personal.demo.entity.Transaction;
import shanks.personal.demo.entity.TransactionType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionResponse {

    private Long id;
    private BigDecimal amount;
    private String description;
    private TransactionType type;
    private String category;
    private LocalDateTime transactionDate;
    private LocalDateTime createdAt;

    // only the userId from the user object -- no nested user, no password
    private Long userId;

    public static TransactionResponse from(Transaction transaction) {
        TransactionResponse response = new TransactionResponse();
        response.setId(transaction.getId());
        response.setAmount(transaction.getAmount());
        response.setDescription(transaction.getDescription());
        response.setType(transaction.getType());
        response.setCategory(transaction.getCategory());
        response.setTransactionDate(transaction.getTransactionDate());
        response.setCreatedAt(transaction.getCreatedAt());
        response.setUserId(transaction.getUser().getId());
        return response;
    }
}
