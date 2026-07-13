package metrics;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.HTTPServer;

import java.io.IOException;
import java.net.InetSocketAddress;

public class MetricsServer {

    private final int port;
    private final CollectorRegistry registry;
    private final Gauge blockHeight;
    private final Counter blocksTotal;
    private final Counter transactionsTotal;
    private final Counter gasUsedTotal;
    private final Gauge pendingTransactions;
    private HTTPServer httpServer;

    public MetricsServer(int port) {
        this.port = port;
        this.registry = new CollectorRegistry();

        blockHeight = Gauge.build()
            .name("depchain_block_height")
            .help("Latest committed block number")
            .register(registry);

        blocksTotal = Counter.build()
            .name("depchain_blocks")
            .help("Total blocks committed since node start")
            .register(registry);

        transactionsTotal = Counter.build()
            .name("depchain_transactions")
            .help("Total transactions processed")
            .register(registry);

        gasUsedTotal = Counter.build()
            .name("depchain_gas_used")
            .help("Total gas consumed")
            .register(registry);

        pendingTransactions = Gauge.build()
            .name("depchain_pending_transactions")
            .help("Current pending transaction pool size")
            .register(registry);
    }

    public void start() throws IOException {
        httpServer = new HTTPServer(new InetSocketAddress(port), registry);
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop();
        }
    }

    public void setBlockHeight(long height) {
        blockHeight.set(height);
    }

    public void incBlocks(long txCount, long gasUsed) {
        blocksTotal.inc();
        transactionsTotal.inc(txCount);
        gasUsedTotal.inc(gasUsed);
    }

    public void setPendingTransactions(int count) {
        pendingTransactions.set(count);
    }
}
