package valintapistemigration;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.zaxxer.hikari.HikariDataSource;
import org.bson.Document;
import org.dalesbred.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static valintapistemigration.DocumentToPisteet.*;
import static valintapistemigration.DocumentToPisteet.documentToHakemus;
import static valintapistemigration.SubscriptionHelper.subscriber;

public class Valintapistemigration {
    private static final Logger LOG = LoggerFactory.getLogger(Valintapistemigration.class);
    private static final AtomicInteger NUMBER_OF_HAKEMUKSIA_MONGOSTA = new AtomicInteger(0);
    private static final AtomicInteger NUMBER_OF_HAKEMUKSIA_WITH_PISTEET = new AtomicInteger(0);
    private static final AtomicLong NUMBER_OF_HAKEMUKSIA_IN_MONGO = new AtomicLong(-1);
    private static final AtomicInteger NUMBER_OF_PISTEET_STORED_TO_POSTGRE = new AtomicInteger(0);
    private static final AtomicInteger NUMBER_OF_HAKEMUKSIA_STORED_TO_POSTGRE = new AtomicInteger(0);
    private static final String TALLENTAJA = "migraatio";
    private static final int TARGET_BATCH_SIZE = 1000;

    public static void main(String[] args) {
        Optional<String> mongoURIarg = Stream.of(args).filter(a -> a.startsWith("mongoURI=")).map(a -> a.replaceFirst("mongoURI=", "")).findAny();
        Optional<String> jdbcURIarg = Stream.of(args).filter(a -> a.startsWith("jdbcURI=")).map(a -> a.replaceFirst("jdbcURI=", "")).findAny();

        String mongoURI = mongoURIarg.orElseGet(() -> System.getProperty("mongoURI"));
        String jdbcURI = jdbcURIarg.orElseGet(() -> System.getProperty("jdbcURI"));
        LOG.warn("Starting Valintapistemigration from mongoURI {} to jdbcURI {}", mongoURI, jdbcURI);


        final HikariDataSource datasource = datasource(jdbcURI);
        final MongoClient mongoClient = MongoClients.create(mongoURI);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            datasource.close();
            mongoClient.close();
            LOG.error("SHUTDOWN DONE!");
        }));

        waitFor(5);

        final ExecutorService executorService = Executors.newWorkStealingPool();
        final ArrayBlockingQueue<List<ValintapisteDAO.PisteRow>> queue = new ArrayBlockingQueue<>(1038346);
        final ValintapisteDAO dao = new ValintapisteDAO(TALLENTAJA, Database.forDataSource(datasource));

        Runnable storeDocumentToPostgresql = () -> {
            try {

                List<ValintapisteDAO.PisteRow> pisteRows = QueueUtil.gatherRowsUntilTargetBatchSize(queue, TARGET_BATCH_SIZE);
                if(!pisteRows.isEmpty()) {
                    LOG.info("Running PostgreSQL update! Batch updating {} rows!", pisteRows.size());
                    dao.insertBatch(pisteRows);
                    LOG.info("DONE INSERTING TO POSTGRESQL!");
                    NUMBER_OF_HAKEMUKSIA_STORED_TO_POSTGRE.addAndGet(pisteRows.size());
                }
            } catch (Throwable e) {
                e.printStackTrace();
                System.out.println(e.getMessage());
            }
        };

        MongoDatabase database = mongoClient.getDatabase("hakulomake");
        MongoCollection<Document> collection = database.getCollection("application");

        collection.count().subscribe(subscriber(count -> NUMBER_OF_HAKEMUKSIA_IN_MONGO.set(count)));

        Consumer<Document> handleDocument = (document) -> {
            int i = NUMBER_OF_HAKEMUKSIA_MONGOSTA.incrementAndGet();
            long overall = NUMBER_OF_HAKEMUKSIA_IN_MONGO.get();
            LOG.info("PROCESSED! {} / {} which is {}%", i, overall, new BigDecimal((((double)i)/((double)overall)) * 100d, new MathContext(2, RoundingMode.HALF_EVEN)).toString());

            Optional<List<ValintapisteDAO.PisteRow>> pisteRows = documentToRows(document);
            pisteRows.ifPresent(rows -> {
                NUMBER_OF_HAKEMUKSIA_WITH_PISTEET.incrementAndGet();
                LOG.info("Adding to queue! (size = {})", queue.size());
                queue.add(rows);
                executorService.submit(storeDocumentToPostgresql);
            });
        };
        collection.find().subscribe(subscriber(handleDocument));

        waitFor(TimeUnit.DAYS.toSeconds(7)); // try maximum of week to complete the migration
    }



    public static HikariDataSource datasource(String uri) { //, String username, String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setMaximumPoolSize(4);
        ds.setJdbcUrl(uri);
        return ds;
    }

    public static void waitFor(long seconds) {
        if(seconds <= 0) {

        } else {
            LOG.warn("...{}", seconds);
            try {
                Thread.sleep(1000L);
            } catch (Exception e) {

            }
            waitFor(seconds -1);
        }
    }
}
