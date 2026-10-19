package com.wassim.commerce.order;

import com.wassim.commerce.order.enums.Currency;
import com.wassim.commerce.order.product.persistence.ProductRepository;
import com.wassim.commerce.order.product.domain.Product;
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

    @Test
    void getsExistingProductThroughPublicEndpoint() throws Exception {
        Product product = new Product();
        product.setProductName("Coffee");
        product.setProductDescription("Beans");
        product.setProductPrice(new BigDecimal("25.10"));
        product.setProductQuantity(7);
        Product saved = repository.saveAndFlush(product);

        var response = get("/api/products/" + saved.getId());
        assertEquals(200, response.statusCode(), response.body());
        var body = json.readTree(response.body());
        assertEquals(saved.getId().longValue(), body.get("id").asLong());
        assertEquals("Coffee", body.get("name").asText());
        assertEquals("Beans", body.get("description").asText());
        assertEquals(0, new BigDecimal("25.10").compareTo(new BigDecimal(body.get("price").asText())));
        assertEquals(7, body.get("stockQuantity").asInt());
        assertEquals("MAD", body.get("currency").asText());
        assertEquals(404, get("/api/admin/products/" + saved.getId()).statusCode());
    }

    @Test
    void missingProductReturns404FromExceptionHandler() throws Exception {
        long id = Long.MAX_VALUE;
        var response = get("/api/products/" + id);
        assertEquals(404, response.statusCode(), response.body());
        var body = json.readTree(response.body());
        assertEquals(404, body.get("status").asInt());
        assertEquals("Not Found", body.get("error").asText());
        assertEquals("Can't find product with id " + id, body.get("message").asText());
        assertTrue(body.hasNonNull("timestamp"));
    }


    @Test
    void combinesNameWithInclusiveDecimalPriceBoundaries() throws Exception {
        saveProduct("Coffee below", "25.49");
        Product lower = saveProduct("Coffee lower", "25.50");
        Product upper = saveProduct("Coffee upper", "26.75");
        saveProduct("Coffee above", "26.76");
        saveProduct("Tea", "25.50");

        var response = get("/api/products?name=COFFEE&minPrice=25.50&maxPrice=26.75&size=1");
        assertEquals(200, response.statusCode(), response.body());
        var first = json.readTree(response.body());
        assertEquals(lower.getId().longValue(), first.get("content").get(0).get("id").asLong());
        assertTrue(first.get("hasNext").asBoolean());

        var second = get("/api/products?name=COFFEE&minPrice=25.50&maxPrice=26.75&size=1&page=1");
        assertEquals(200, second.statusCode(), second.body());
        var last = json.readTree(second.body());
        assertEquals(upper.getId().longValue(), last.get("content").get(0).get("id").asLong());
        assertFalse(last.get("hasNext").asBoolean());

        var equal = get("/api/products?name=coffee&minPrice=25.50&maxPrice=25.50");
        assertEquals(200, equal.statusCode(), equal.body());
        var content = json.readTree(equal.body()).get("content");
        assertEquals(1, content.size());
        assertEquals(lower.getId().longValue(), content.get(0).get("id").asLong());
    }

    @ParameterizedTest
    @MethodSource("invalidFilterParameters")
    void invalidFilterReturns400(String parameters) throws Exception {
        var response = get("/api/products?" + parameters);
        assertEquals(400, response.statusCode(), response.body());
    }

    static Stream<String> invalidFilterParameters() {
        return Stream.of("page=-1", "size=0", "size=-1", "size=101", "page=abc", "size=1.5",
                "minPrice=-0.01", "maxPrice=-1", "minPrice=3&maxPrice=2", "minPrice=abc",
                "minPrice=1.001", "sortBy=unknown", "sortBy=price,up", "sortBy=productPrice",
                "sortBy=", "sortBy=id,asc,desc");
    }

    @Test
    void defaultsToTwentySortedById() throws Exception {
        var expected = new java.util.ArrayList<Long>();
        for (int i = 0; i < 21; i++) {
            expected.add(saveProduct("Product " + i, "1.00").getId());
        }
        var response = get("/api/products");
        assertEquals(200, response.statusCode(), response.body());
        var body = json.readTree(response.body());
        assertEquals(0, body.get("page").asInt());
        assertEquals(20, body.get("size").asInt());
        assertEquals(20, body.get("content").size());
        assertTrue(body.get("hasNext").asBoolean());
        for (int i = 0; i < 20; i++) {
            assertEquals(expected.get(i).longValue(), body.get("content").get(i).get("id").asLong());
        }
        assertFalse(body.has("totalElements"));
        var last = get("/api/products?page=1");
        assertEquals(200, last.statusCode(), last.body());
        var lastBody = json.readTree(last.body());
        assertEquals(1, lastBody.get("content").size());
        assertEquals(expected.get(20).longValue(), lastBody.get("content").get(0).get("id").asLong());
        assertFalse(lastBody.get("hasNext").asBoolean());

        var maximum = get("/api/products?size=100");
        assertEquals(200, maximum.statusCode(), maximum.body());
        assertEquals(21, json.readTree(maximum.body()).get("content").size());
    }

    @ParameterizedTest
    @MethodSource("sortCases")
    void sortsAcrossPopulatedPagesWithIdTieBreaker(String sortBy, String order) throws Exception {
        Product a = saveProduct("B", "20.00");
        Product b = saveProduct("A", "10.00");
        Product c = saveProduct("B", "20.00");
        Product d = saveProduct("A", "10.00");
        var ids = java.util.List.of(a.getId(), b.getId(), c.getId(), d.getId());
        for (int page = 0; page < 2; page++) {
            var response = get("/api/products?size=2&page=" + page + "&sortBy=" + sortBy);
            assertEquals(200, response.statusCode(), response.body());
            var body = json.readTree(response.body());
            assertEquals(2, body.get("content").size());
            assertEquals(page == 0, body.get("hasNext").asBoolean());
            for (int i = 0; i < 2; i++) {
                int expectedIndex = order.charAt(page * 2 + i) - '0';
                assertEquals(ids.get(expectedIndex).longValue(), body.get("content").get(i).get("id").asLong());
            }
        }
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> sortCases() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("id,asc", "0123"),
                org.junit.jupiter.params.provider.Arguments.of("id,desc", "3210"),
                org.junit.jupiter.params.provider.Arguments.of("name,asc", "1302"),
                org.junit.jupiter.params.provider.Arguments.of("name,desc", "0213"),
                org.junit.jupiter.params.provider.Arguments.of("price,asc", "1302"),
                org.junit.jupiter.params.provider.Arguments.of("price,desc", "0213"));
    }

    @Test
    void updatesPriceAndDescription() throws Exception {
        Product product = saveProduct("Original", "25.10");
        var response = patch(product.getId(), "{\"price\":78983,\"description\":\"Hello\",\"version\":" + product.getVersion() + "}");
        assertEquals(200, response.statusCode());

        Product saved = repository.findById(product.getId()).orElseThrow();
        assertEquals(product.getVersion() + 1, saved.getVersion().longValue());
        assertEquals(saved.getVersion().longValue(), json.readTree(response.body()).get("version").asLong());
        assertEquals(new BigDecimal("78983.00"), saved.getProductPrice());
        assertEquals("Hello", saved.getProductDescription());
        assertEquals("Original", saved.getProductName());
        assertEquals(7, saved.getProductQuantity());
        assertEquals(Currency.MAD, saved.getCurrency());
    }

    @Test
    void rejectsInvalidUpdate() throws Exception {
        Product product = saveProduct("Original", "25.10");
        assertEquals(400, patch(product.getId(), "{\"price\":-1,\"version\":" + product.getVersion() + "}").statusCode());
        assertEquals(400, patch(product.getId(), "{\"name\":\"   \",\"version\":" + product.getVersion() + "}").statusCode());
        assertEquals(400, patch(product.getId(), "{\"price\":30}").statusCode());
        Product saved = repository.findById(product.getId()).orElseThrow();
        assertEquals(product.getVersion(), saved.getVersion());
        assertEquals(new BigDecimal("25.10"), saved.getProductPrice());
        assertEquals("Original", saved.getProductName());
    }

    @Test
    void updateMissingProductReturns404() throws Exception {
        assertEquals(404, patch(Long.MAX_VALUE, "{\"description\":\"Hello\",\"version\":0}").statusCode());
    }

    @Test
    void rejectsOldVersionWithoutChangingProduct() throws Exception {
        Product product = saveProduct("Original", "25.10");
        Long oldVersion = product.getVersion();
        assertEquals(200, patch(product.getId(), "{\"price\":30,\"version\":" + oldVersion + "}").statusCode());

        var response = patch(product.getId(), "{\"price\":40,\"version\":" + oldVersion + "}");
        assertEquals(409, response.statusCode());
        Product saved = repository.findById(product.getId()).orElseThrow();
        assertEquals(new BigDecimal("30.00"), saved.getProductPrice());
        assertEquals("Original", saved.getProductName());
        assertEquals(oldVersion + 1, saved.getVersion().longValue());
    }

    private HttpResponse<String> patch(long id, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/products/" + id))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private Product saveProduct(String name, String price) {
        Product product = new Product();
        product.setProductName(name);
        product.setProductPrice(new BigDecimal(price));
        product.setProductQuantity(7);
        return repository.saveAndFlush(product);
    }

    private HttpResponse<String> get(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/admin/products"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
