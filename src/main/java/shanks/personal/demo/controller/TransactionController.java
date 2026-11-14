package shanks.personal.demo.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import shanks.personal.demo.dto.CreateTransactionRequest;
import shanks.personal.demo.dto.SummaryResponse;
import shanks.personal.demo.dto.TransactionResponse;
import shanks.personal.demo.dto.UpdateTransactionRequest;
import shanks.personal.demo.entity.Transaction;
import shanks.personal.demo.service.TransactionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/transaction")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @Valid @RequestBody CreateTransactionRequest request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        Transaction created = transactionService.createTransaction(userId, request);
        return ResponseEntity.ok(TransactionResponse.from(created));
    }

    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> getMyTransactions(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = (Long) request.getAttribute("userId");

        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        return ResponseEntity.ok(transactionService.getTransactionsForUser(userId, pageable));
    }

    @PutMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> updateTransaction(
            @PathVariable Long transactionId,
            @Valid @RequestBody UpdateTransactionRequest request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        Transaction updated = transactionService.updateTransaction(transactionId, userId, request);
        return ResponseEntity.ok(TransactionResponse.from(updated));
    }

    @DeleteMapping("/{transactionId}")
    public ResponseEntity<Void> deleteTransaction(
            @PathVariable Long transactionId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        transactionService.deleteTransaction(transactionId, userId);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/summary")
    public ResponseEntity<SummaryResponse> getSummary(
            HttpServletRequest request,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        Long userId = (Long) request.getAttribute("userId");
        return ResponseEntity.ok(transactionService.getSummary(userId, month != null ? month : YearMonth.now()));
    }

}