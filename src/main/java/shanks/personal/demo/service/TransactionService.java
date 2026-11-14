package shanks.personal.demo.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.RequestMapping;
import shanks.personal.demo.dto.CreateTransactionRequest;
import shanks.personal.demo.dto.SummaryResponse;
import shanks.personal.demo.dto.TransactionResponse;
import shanks.personal.demo.dto.UpdateTransactionRequest;
import shanks.personal.demo.entity.Transaction;
import shanks.personal.demo.entity.TransactionType;
import shanks.personal.demo.entity.User;
import shanks.personal.demo.exceptions.NotFoundException;
import shanks.personal.demo.exceptions.UnauthorizedException;
import shanks.personal.demo.repository.TransactionRepository;
import shanks.personal.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

@RequestMapping("/transactions")
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public Transaction createTransaction(Long userId, CreateTransactionRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setAmount(request.getAmount());
        transaction.setDescription(request.getDescription());
        transaction.setType(request.getType());
        transaction.setCategory(request.getCategory());
        transaction.setTransactionDate(request.getTransactionDate());

        return transactionRepository.save(transaction);
    }

    public Page<TransactionResponse> getTransactionsForUser(Long userId, Pageable pageable) {
        return transactionRepository
                .findByUserIdOrderByTransactionDateDesc(userId, pageable)
                .map(TransactionResponse::from);
    }
    public void deleteTransaction(Long transactionId, Long userId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new NotFoundException("Transaction not found with id: " + transactionId));

        if (!transaction.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Unauthorized: this transaction does not belong to you");
        }

        transactionRepository.delete(transaction);
    }

    public Transaction updateTransaction(Long transactionId, Long userId, UpdateTransactionRequest request) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new NotFoundException("Transaction not found with id: " + transactionId));

        if (!transaction.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("This transaction does not belong to you");
        }

        if (request.getAmount() != null) {
            transaction.setAmount(request.getAmount());
        }
        if (request.getDescription() != null) {
            transaction.setDescription(request.getDescription());
        }
        if (request.getCategory() != null) {
            transaction.setCategory(request.getCategory());
        }
        if (request.getType() != null) {
            transaction.setType(request.getType());
        }
        if (request.getTransactionDate() != null) {
            transaction.setTransactionDate(request.getTransactionDate());
        }

        return transactionRepository.save(transaction);
    }

    public SummaryResponse getSummary(Long userId, YearMonth month) {
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();

        BigDecimal income = Objects.requireNonNullElse(
                transactionRepository.sumByType(userId, TransactionType.INCOME, start, end), BigDecimal.ZERO);
        BigDecimal expense = Objects.requireNonNullElse(
                transactionRepository.sumByType(userId, TransactionType.EXPENSE, start, end), BigDecimal.ZERO);

        return new SummaryResponse(income, expense, income.subtract(expense),
                transactionRepository.expenseByCategory(userId, start, end));
    }
}