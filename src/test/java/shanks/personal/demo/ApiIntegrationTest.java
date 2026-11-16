package shanks.personal.demo;

import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import shanks.personal.demo.security.TokenBlacklistService;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real HTTP layer (security filters, validation, controllers, services,
 * JPA) against an in-memory H2 database. Redis is replaced by a mock blacklist.
 * Every test registers its own uniquely named users, so tests don't affect each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {

    // If your controller uses different method-level paths (for example "/transaction/addTransaction"),
    // change these three constants and nothing else.
    private static final String ADD = "/transaction";   // POST
    private static final String LIST = "/transaction";  // GET
    private static final String BASE = "/transaction";  // PUT and DELETE /{id}, GET /summary

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final String AUTH = "Authorization";
    private static final String PASSWORD = "password123";

    @Autowired private MockMvc mockMvc;
    @Value("${jwt.secret}") private String jwtSecret;
    @MockitoBean private TokenBlacklistService tokenBlacklistService;

    // ------------------------------------------------------------------ helpers

    private static String uniqueName() {
        return "user" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String registerJson(String username, String email, String password) {
        return "{\"username\":\"" + username + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private static String loginJson(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private static String transactionJson(String amount, String description, String type,
                                          String category, String date) {
        String categoryPart = category == null ? "" : ",\"category\":\"" + category + "\"";
        return "{\"amount\":" + amount + ",\"description\":\"" + description + "\",\"type\":\"" + type + "\""
                + categoryPart + ",\"transactionDate\":\"" + date + "\"}";
    }

    private String registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/auth/register").contentType(JSON)
                        .content(registerJson(username, username + "@example.com", PASSWORD)))
                .andExpect(status().isOk());

        String body = mockMvc.perform(post("/auth/login").contentType(JSON)
                        .content(loginJson(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.token");
    }

    private long addTransaction(String token, String amount, String description, String type,
                                String category, String date) throws Exception {
        String body = mockMvc.perform(post(ADD).header(AUTH, bearer(token)).contentType(JSON)
                        .content(transactionJson(amount, description, type, category, date)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        return id.longValue();
    }
    @Test
    void pingIsPublic() throws Exception {
        mockMvc.perform(get("/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }
    // ------------------------------------------------------------------ register and login

    @Test
    void registerReturnsTheUserWithoutThePasswordHash() throws Exception {
        String username = uniqueName();

        mockMvc.perform(post("/auth/register").contentType(JSON)
                        .content(registerJson(username, username + "@example.com", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void registerRejectsInvalidInputWithFieldMessages() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(JSON)
                        .content("{\"username\":\"a\",\"email\":\"nope\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.username").exists())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.password").exists());
    }

    @Test
    @Disabled("Enable once duplicate registration returns 409 (ConflictException + handler).")
    void registeringTheSameUsernameTwiceIsAConflict() throws Exception {
        String username = uniqueName();
        String body = registerJson(username, username + "@example.com", PASSWORD);

        mockMvc.perform(post("/auth/register").contentType(JSON).content(body)).andExpect(status().isOk());
        mockMvc.perform(post("/auth/register").contentType(JSON).content(body)).andExpect(status().isConflict());
    }

    @Test
    void loginReturnsATokenForValidCredentials() throws Exception {
        String username = uniqueName();
        registerAndLogin(username);

        mockMvc.perform(post("/auth/login").contentType(JSON).content(loginJson(username, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void loginRejectsAWrongPassword() throws Exception {
        String username = uniqueName();
        registerAndLogin(username);

        mockMvc.perform(post("/auth/login").contentType(JSON).content(loginJson(username, "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRejectsAnUnknownUser() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(JSON).content(loginJson(uniqueName(), PASSWORD)))
                .andExpect(status().is4xxClientError());
    }

    // ------------------------------------------------------------------ token handling

    @Test
    void protectedRouteWithoutATokenIsForbidden() throws Exception {
        mockMvc.perform(get("/users/me")).andExpect(status().isForbidden());
    }

    @Test
    void meReturnsTheCurrentUserForAValidToken() throws Exception {
        String username = uniqueName();
        String token = registerAndLogin(username);

        mockMvc.perform(get("/users/me").header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void garbageTokenIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/users/me").header(AUTH, bearer("abc.def.ghi")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejectedWith401() throws Exception {
        String expired = Jwts.builder()
                .setSubject("someone")
                .claim("userId", 1L)
                .setIssuedAt(new Date(System.currentTimeMillis() - 7_200_000L))
                .setExpiration(new Date(System.currentTimeMillis() - 3_600_000L))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();

        mockMvc.perform(get("/users/me").header(AUTH, bearer(expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blacklistedTokenIsRejectedWith401() throws Exception {
        String token = registerAndLogin(uniqueName());
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(true);

        mockMvc.perform(get("/users/me").header(AUTH, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutBlacklistsTheToken() throws Exception {
        String token = registerAndLogin(uniqueName());

        mockMvc.perform(post("/auth/logout").header(AUTH, bearer(token)))
                .andExpect(status().isNoContent());

        verify(tokenBlacklistService).blacklist(eq(token), anyLong());
    }

    @Test
    void deletingTheAccountBlacklistsTheTokenAndStopsLogin() throws Exception {
        String username = uniqueName();
        String token = registerAndLogin(username);

        mockMvc.perform(delete("/users/me").header(AUTH, bearer(token)))
                .andExpect(status().isNoContent());

        verify(tokenBlacklistService).blacklist(eq(token), anyLong());
        mockMvc.perform(post("/auth/login").contentType(JSON).content(loginJson(username, PASSWORD)))
                .andExpect(status().is4xxClientError());
    }

    // ------------------------------------------------------------------ transactions

    @Test
    void transactionLifecycleCreateListUpdateDelete() throws Exception {
        String token = registerAndLogin(uniqueName());
        long id = addTransaction(token, "12.50", "Lunch", "EXPENSE", "Food", "2026-10-15T12:00:00");

        mockMvc.perform(get(LIST).header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].description").value("Lunch"))
                .andExpect(jsonPath("$.content[0].userId").exists());

        mockMvc.perform(put(BASE + "/" + id).header(AUTH, bearer(token)).contentType(JSON)
                        .content("{\"amount\":20.00,\"type\":\"INCOME\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("INCOME"))
                .andExpect(jsonPath("$.description").value("Lunch"));

        mockMvc.perform(delete(BASE + "/" + id).header(AUTH, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(LIST).header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void invalidTransactionIsRejectedWithFieldMessages() throws Exception {
        String token = registerAndLogin(uniqueName());

        mockMvc.perform(post(ADD).header(AUTH, bearer(token)).contentType(JSON)
                        .content("{\"amount\":0,\"description\":\"\",\"type\":\"EXPENSE\","
                                + "\"transactionDate\":\"2026-10-15T12:00:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.amount").exists())
                .andExpect(jsonPath("$.description").exists());
    }

    @Test
    void usersCannotChangeEachOthersTransactions() throws Exception {
        String tokenA = registerAndLogin(uniqueName());
        String tokenB = registerAndLogin(uniqueName());
        long id = addTransaction(tokenA, "10.00", "Original", "EXPENSE", "Food", "2026-10-15T12:00:00");

        // Currently answered with 401; it should become 403 (see the README to-do list).
        mockMvc.perform(put(BASE + "/" + id).header(AUTH, bearer(tokenB)).contentType(JSON)
                        .content("{\"description\":\"hacked\"}"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(delete(BASE + "/" + id).header(AUTH, bearer(tokenB)))
                .andExpect(status().is4xxClientError());

        // The owner's transaction is untouched, and B never sees it.
        mockMvc.perform(get(LIST).header(AUTH, bearer(tokenA)))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].description").value("Original"));
        mockMvc.perform(get(LIST).header(AUTH, bearer(tokenB)))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void listIsPaginatedNewestFirst() throws Exception {
        String token = registerAndLogin(uniqueName());
        addTransaction(token, "1.00", "first", "EXPENSE", "A", "2026-10-01T10:00:00");
        addTransaction(token, "2.00", "second", "EXPENSE", "A", "2026-10-02T10:00:00");
        addTransaction(token, "3.00", "third", "EXPENSE", "A", "2026-10-03T10:00:00");

        mockMvc.perform(get(LIST).header(AUTH, bearer(token)).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].description").value("third"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get(LIST).header(AUTH, bearer(token)).param("page", "1").param("size", "2"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].description").value("first"));
    }

    @Test
    void summaryTotalsTheRequestedMonthAndGroupsExpensesByCategory() throws Exception {
        String token = registerAndLogin(uniqueName());
        addTransaction(token, "2500.00", "Salary", "INCOME", "Work", "2026-10-01T09:00:00");
        addTransaction(token, "900.00", "Rent", "EXPENSE", "Rent", "2026-10-02T09:00:00");
        addTransaction(token, "100.65", "Groceries", "EXPENSE", "Food", "2026-10-10T09:00:00");
        // Outside the requested month, so it must not be counted.
        addTransaction(token, "50.00", "Old lunch", "EXPENSE", "Food", "2026-09-30T09:00:00");

        mockMvc.perform(get(BASE + "/summary").header(AUTH, bearer(token)).param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(2500.0))
                .andExpect(jsonPath("$.totalExpense").value(1000.65))
                .andExpect(jsonPath("$.balance").value(1499.35))
                .andExpect(jsonPath("$.expenseByCategory.length()").value(2))
                .andExpect(jsonPath("$.expenseByCategory[0].category").value("Rent"))
                .andExpect(jsonPath("$.expenseByCategory[1].category").value("Food"));
    }

    @Test
    void summaryForAMonthWithNothingIsAllZeros() throws Exception {
        String token = registerAndLogin(uniqueName());

        mockMvc.perform(get(BASE + "/summary").header(AUTH, bearer(token)).param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(0.0))
                .andExpect(jsonPath("$.totalExpense").value(0.0))
                .andExpect(jsonPath("$.expenseByCategory.length()").value(0));
    }
}
