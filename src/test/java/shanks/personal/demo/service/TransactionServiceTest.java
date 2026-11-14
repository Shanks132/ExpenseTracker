package shanks.personal.demo.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import shanks.personal.demo.dto.CategoryTotal;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private TransactionService transactionService;

    private static User user(long id) {
        User u = new User();
        u.setId(id);
        u.setUsername("user" + id);
        return u;
    }

    private static Transaction transaction(long id, User owner) {
        Transaction t = new Transaction();
        t.setId(id);
        t.setUser(owner);
        t.setAmount(new BigDecimal("100.00"));
        t.setDescription("Original");
        t.setType(TransactionType.EXPENSE);
        t.setCategory("Food");
        t.setTransactionDate(LocalDateTime.of(2026, 10, 15, 12, 0));
        return t;
    }

    // ---------- create ----------

    @Test
    void createTransactionLinksItToTheUserAndCopiesTheFields() {
        User owner = user(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("12.50"));
        request.setDescription("Lunch");
        request.setType(TransactionType.EXPENSE);
        request.setCategory("Food");
        request.setTransactionDate(LocalDateTime.of(2026, 10, 25, 12, 0));

        Transaction saved = transactionService.createTransaction(1L, request);

        assertThat(saved.getUser()).isSameAs(owner);
        assertThat(saved.getAmount()).isEqualByComparingTo("12.50");
        assertThat(saved.getDescription()).isEqualTo("Lunch");
        assertThat(saved.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(saved.getCategory()).isEqualTo("Food");
        assertThat(saved.getTransactionDate()).isEqualTo(LocalDateTime.of(2026, 10, 25, 12, 0));
    }

    @Test
    void createTransactionThrowsAndSavesNothingWhenTheUserIsMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.createTransaction(99L, new CreateTransactionRequest()))
                .isInstanceOf(NotFoundException.class);

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    // ---------- list ----------

    @Test
    void getTransactionsForUserMapsEachRowToAResponse() {
        Pageable pageable = PageRequest.of(0, 10);
        Transaction tx = transaction(5L, user(1L));
        when(transactionRepository.findByUserIdOrderByTransactionDateDesc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(tx), pageable, 1));

        Page<TransactionResponse> result = transactionService.getTransactionsForUser(1L, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(5L);
        assertThat(result.getContent().get(0).getUserId()).isEqualTo(1L);
    }

    // ---------- update ----------

    @Test
    void updateTransactionChangesOnlyTheProvidedFields() {
        Transaction tx = transaction(5L, user(1L));
        when(transactionRepository.findById(5L)).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionRequest request = new UpdateTransactionRequest();
        request.setAmount(new BigDecimal("250.00"));
        request.setType(TransactionType.INCOME);

        Transaction updated = transactionService.updateTransaction(5L, 1L, request);

        assertThat(updated.getAmount()).isEqualByComparingTo("250.00");
        assertThat(updated.getType()).isEqualTo(TransactionType.INCOME);
        assertThat(updated.getDescription()).isEqualTo("Original");
        assertThat(updated.getCategory()).isEqualTo("Food");
    }

    @Test
    void updateTransactionRejectsSomeoneElsesTransaction() {
        Transaction tx = transaction(5L, user(2L));
        when(transactionRepository.findById(5L)).thenReturn(Optional.of(tx));

        assertThatThrownBy(() -> transactionService.updateTransaction(5L, 1L, new UpdateTransactionRequest()))
                .isInstanceOf(UnauthorizedException.class);

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void updateTransactionThrowsWhenMissing() {
        when(transactionRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.updateTransaction(5L, 1L, new UpdateTransactionRequest()))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------- delete ----------

    @Test
    void deleteTransactionRemovesTheOwnersTransaction() {
        Transaction tx = transaction(5L, user(1L));
        when(transactionRepository.findById(5L)).thenReturn(Optional.of(tx));

        transactionService.deleteTransaction(5L, 1L);

        verify(transactionRepository).delete(tx);
    }

    @Test
    void deleteTransactionRejectsSomeoneElsesTransaction() {
        Transaction tx = transaction(5L, user(2L));
        when(transactionRepository.findById(5L)).thenReturn(Optional.of(tx));

        assertThatThrownBy(() -> transactionService.deleteTransaction(5L, 1L))
                .isInstanceOf(UnauthorizedException.class);

        verify(transactionRepository, never()).delete(any(Transaction.class));
    }

    @Test
    void deleteTransactionThrowsWhenMissing() {
        when(transactionRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.deleteTransaction(5L, 1L))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------- summary ----------

    @Test
    void getSummaryTotalsTheMonthAndComputesTheBalance() {
        YearMonth month = YearMonth.of(2026, 10);
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 1, 0, 0);
        List<CategoryTotal> categories = List.of(
                new CategoryTotal("Rent", new BigDecimal("900.00")),
                new CategoryTotal("Food", new BigDecimal("100.65")));

        when(transactionRepository.sumByType(1L, TransactionType.INCOME, start, end))
                .thenReturn(new BigDecimal("2500.00"));
        when(transactionRepository.sumByType(1L, TransactionType.EXPENSE, start, end))
                .thenReturn(new BigDecimal("1000.65"));
        when(transactionRepository.expenseByCategory(1L, start, end)).thenReturn(categories);

        SummaryResponse summary = transactionService.getSummary(1L, month);

        assertThat(summary.totalIncome()).isEqualByComparingTo("2500.00");
        assertThat(summary.totalExpense()).isEqualByComparingTo("1000.65");
        assertThat(summary.balance()).isEqualByComparingTo("1499.35");
        assertThat(summary.expenseByCategory()).isEqualTo(categories);
    }

    @Test
    void getSummaryReturnsZerosForAMonthWithNoTransactions() {
        YearMonth month = YearMonth.of(2026, 10);
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 1, 0, 0);

        // SUM() over no rows is null in SQL, so the service must treat null as zero.
        when(transactionRepository.sumByType(1L, TransactionType.INCOME, start, end)).thenReturn(null);
        when(transactionRepository.sumByType(1L, TransactionType.EXPENSE, start, end)).thenReturn(null);
        when(transactionRepository.expenseByCategory(1L, start, end)).thenReturn(List.of());

        SummaryResponse summary = transactionService.getSummary(1L, month);

        assertThat(summary.totalIncome()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.totalExpense()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.expenseByCategory()).isEmpty();
    }
}
