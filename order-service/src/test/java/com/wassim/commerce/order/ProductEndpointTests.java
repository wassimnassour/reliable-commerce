package com.wassim.commerce.order;

import com.wassim.commerce.order.enums.Currency;
import com.wassim.commerce.order.product.persistence.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ProductEndpointTests {
    @LocalServerPort
    int port;

    @Autowired
    ProductRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper json = new JsonMapper();

    @BeforeEach
    void clearProducts() {
        repository.deleteAll();
    }

    @Test
    void createsProductThroughHttpAndPersistsExactDecimalValues() throws Exception {
        var response = post("""
                {"name":"Coffee","description":"Beans","price":25.10,"stockQuantity":0,"currency":"MAD"}
                """);
        assertEquals(201, response.statusCode(), response.body());
        var body = json.readTree(response.body());
        long id = body.get("id").asLong();
        assertTrue(id > 0);
        assertEquals(1, repository.count());
        var saved = repository.findById(id).orElseThrow();
        assertEquals("Coffee", saved.getProductName());
        assertEquals("Beans", saved.getProductDescription());
        assertEquals(new BigDecimal("25.10"), saved.getProductPrice());
        assertEquals(0, saved.getProductQuantity());
        assertEquals(Currency.MAD, saved.getCurrency());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertEquals(saved.getProductName(), body.get("name").asText());
        assertEquals(saved.getProductDescription(), body.get("description").asText());
        assertEquals(0, saved.getProductPrice().compareTo(new BigDecimal(body.get("price").asText())));
        assertEquals(0, body.get("stockQuantity").asInt());
        assertEquals("MAD", body.get("currency").asText());
        assertFalse(body.has("productPrice"));
        assertEquals("numeric", jdbc.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'products' AND column_name = 'product_price'
                """, String.class));
        assertEquals(2, jdbc.queryForObject("""
                SELECT numeric_scale FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'products' AND column_name = 'product_price'
                """, Integer.class));
    }

    @Test
    void defaultsOmittedCurrencyToMad() throws Exception {
        var response = post("""
                {"name":"Tea","price":0.01,"stockQuantity":3}
                """);
        assertEquals(201, response.statusCode(), response.body());
        var saved = repository.findById(json.readTree(response.body()).get("id").asLong()).orElseThrow();
        assertEquals(Currency.MAD, saved.getCurrency());
        assertEquals(new BigDecimal("0.01"), saved.getProductPrice());
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsInvalidInputWithoutCreatingRow(String request) throws Exception {
        long before = repository.count();
        var response = post(request);
        assertEquals(400, response.statusCode(), response.body());
        assertEquals(before, repository.count());
    }

    static Stream<String> invalidRequests() {
        return Stream.of(
                """ 
                {"name":" ","price":25.10,"stockQuantity":1}
                """,
                """
                {"price":25.10,"stockQuantity":1}
                """,
                """
                {"name":"Tea","price":0,"stockQuantity":1}
                """,
                """
                {"name":"Tea","price":-1,"stockQuantity":1}
                """,
                """
                {"name":"Tea","stockQuantity":1}
                """,
                """
                {"name":"Tea","price":25.10,"stockQuantity":-1}
                """,
                """
                {"name":"Tea","price":25.10}
                """,
                """
                {"name":"Tea","price":25.10,"stockQuantity":1.5}
                """,
                """
                {"name":"Tea","price":1.001,"stockQuantity":1}
                """,
                """
                {"name":"Tea","price":100000000000000000,"stockQuantity":1}
                """,
                """
                {"productName":"Tea","productPrice":25.10,"productQuantity":1}
                """);
    }

    @Test
    void rejectsUnsupportedCurrencyWithoutCreatingRow() throws Exception {
        long before = repository.count();
        var response = post("""
                {"name":"Tea","price":25.10,"stockQuantity":1,"currency":"USD"}
                """);
        assertEquals(400, response.statusCode(), response.body());
        assertEquals(before, repository.count());
    }

    private HttpResponse<String> post(String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/admin/products"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
