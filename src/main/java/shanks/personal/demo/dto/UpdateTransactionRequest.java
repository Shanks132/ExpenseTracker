package shanks.personal.demo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;
import shanks.personal.demo.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UpdateTransactionRequest {

    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @Size(max = 255, message = "Description too long")
    private String description;

    @Size(max = 100, message = "Category too long")
    private String category;

    private TransactionType type;

    private LocalDateTime transactionDate;
}