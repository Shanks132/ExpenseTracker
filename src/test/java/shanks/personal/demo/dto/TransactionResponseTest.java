package shanks.personal.demo.dto;

import org.junit.jupiter.api.Test;
import shanks.personal.demo.entity.Transaction;
import shanks.personal.demo.entity.TransactionType;
import shanks.personal.demo.entity.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionResponseTest {

    @Test
    void fromCopiesTheFieldsAndExposesOnlyTheUserId() {
        User owner = new User();
        owner.setId(7L);
        owner.setUsername("alice");
        owner.setPassword("secret-hash");

        Transaction transaction = new Transaction();
        transaction.setId(3L);
        transaction.setUser(owner);
        transaction.setAmount(new BigDecimal("12.50"));
        transaction.setDescription("Lunch");
        transaction.setType(TransactionType.EXPENSE);
        transaction.setCategory("Food");
        transaction.setTransactionDate(LocalDateTime.of(2026, 10, 15, 12, 0));

        TransactionResponse response = TransactionResponse.from(transaction);

        assertThat(response.getId()).isEqualTo(3L);
        assertThat(response.getUserId()).isEqualTo(7L);
        assertThat(response.getAmount()).isEqualByComparingTo("12.50");
        assertThat(response.getDescription()).isEqualTo("Lunch");
        assertThat(response.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(response.getCategory()).isEqualTo("Food");
    }

    @Test
    void responseTypeHasNoFieldThatCouldLeakTheUserOrPassword() {
        assertThat(Arrays.stream(TransactionResponse.class.getDeclaredFields()).map(f -> f.getName()))
                .doesNotContain("user", "password");
    }
}
