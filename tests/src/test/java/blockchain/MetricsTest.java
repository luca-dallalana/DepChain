package blockchain;

import metrics.MetricsServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

public class MetricsTest {

    private static final int PORT = 19090;
    private static final String METRICS_URL = "http://localhost:" + PORT + "/metrics";

    private static MetricsServer metricsServer;
    private static HttpClient httpClient;

    @BeforeAll
    static void startServer() throws Exception {
        metricsServer = new MetricsServer(PORT);
        metricsServer.start();
        httpClient = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stopServer() {
        if (metricsServer != null) metricsServer.stop();
    }

    private HttpResponse<String> fetchMetrics() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(METRICS_URL))
            .GET()
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void testMetricsEndpointReturns200() throws Exception {
        assertEquals(200, fetchMetrics().statusCode());
    }

    @Test
    void testContentTypeIsTextPlain() throws Exception {
        String contentType = fetchMetrics().headers().firstValue("content-type").orElse("");
        assertTrue(contentType.startsWith("text/plain"), "Content-Type must be text/plain, got: " + contentType);
    }

    @Test
    void testBlockHeightMetricPresent() throws Exception {
        assertTrue(fetchMetrics().body().contains("depchain_block_height"));
    }

    @Test
    void testBlocksTotalMetricPresent() throws Exception {
        assertTrue(fetchMetrics().body().contains("depchain_blocks_total"));
    }

    @Test
    void testTransactionsTotalMetricPresent() throws Exception {
        assertTrue(fetchMetrics().body().contains("depchain_transactions_total"));
    }

    @Test
    void testGasUsedTotalMetricPresent() throws Exception {
        assertTrue(fetchMetrics().body().contains("depchain_gas_used_total"));
    }

    @Test
    void testPendingTransactionsMetricPresent() throws Exception {
        assertTrue(fetchMetrics().body().contains("depchain_pending_transactions"));
    }

    @Test
    void testBlockHeightReflectsSetValue() throws Exception {
        metricsServer.setBlockHeight(5);
        assertTrue(fetchMetrics().body().contains("depchain_block_height 5.0"),
            "Block height metric must reflect set value of 5");
    }

    @Test
    void testTransactionCounterIncrements() throws Exception {
        metricsServer.incBlocks(3, 0);
        assertTrue(fetchMetrics().body().contains("depchain_transactions_total 3.0"),
            "Transaction counter must reflect incremented value");
    }

    @Test
    void testGasCounterIncrements() throws Exception {
        metricsServer.incBlocks(0, 21000);
        assertTrue(fetchMetrics().body().contains("depchain_gas_used_total 21000.0"),
            "Gas counter must reflect incremented value");
    }
}
