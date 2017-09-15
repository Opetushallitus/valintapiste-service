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
import valintapistemigration.utils.ProcessCounter;
import valintapistemigration.utils.QueueUtil;
import valintapistemigration.utils.ValintapisteDAO;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static valintapistemigration.utils.DocumentToPisteet.*;
import static valintapistemigration.utils.SubscriptionHelper.subscriber;

public class Valintapistemigration {
    private static final Logger LOG = LoggerFactory.getLogger(Valintapistemigration.class);
    private static final ProcessCounter COUNTER = new ProcessCounter();
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
            Map.Entry<Long, List<ValintapisteDAO.PisteRow>> hakemuksiaLuettuJaPisteitä = QueueUtil.gatherRowsUntilTargetBatchSize(queue, TARGET_BATCH_SIZE);
            final Long hakemuksiaLuettu = hakemuksiaLuettuJaPisteitä.getKey();
            final List<ValintapisteDAO.PisteRow> pisteRows = hakemuksiaLuettuJaPisteitä.getValue();
            try {
                if (!pisteRows.isEmpty()) {
                    LOG.info("Running PostgreSQL update! Batch updating {} rows!", pisteRows.size());
                    dao.insertBatch(pisteRows);
                    LOG.debug("DONE INSERTING TO POSTGRESQL!");
                }
            } catch (Throwable e) {
                e.printStackTrace();
                System.out.println(e.getMessage());
                COUNTER.hakemuksenKäsittelyEpäonnistui(hakemuksiaLuettu);
            } finally {
                COUNTER.hakemusKäsitelty(hakemuksiaLuettu);
            }
        };

        MongoDatabase database = mongoClient.getDatabase("hakulomake");
        MongoCollection<Document> collection = database.getCollection("application");

        collection.count().subscribe(subscriber(count -> COUNTER.setHakemuksiaMongossa(count)));

        Consumer<Document> handleDocument = (document) -> {
            COUNTER.hakemusLuettuMongosta();
            List<ValintapisteDAO.PisteRow> pisteRows = documentToRows(document);
            boolean hakemuksellaTallennettaviaPisteita = pisteRows.isEmpty();
            if(hakemuksellaTallennettaviaPisteita) {
                LOG.debug("Adding to queue! (size = {})", queue.size());
                queue.add(pisteRows);
                executorService.submit(storeDocumentToPostgresql);
            } else { // skipping hakemus with no pisteet
                COUNTER.hakemusKäsitelty(1L);
            }
        };
        collection.find().subscribe(subscriber(handleDocument));

        waitFor(TimeUnit.DAYS.toSeconds(7), () -> {
            return String.format("(queue size currently = %s) %s", queue.size(), COUNTER.toString());
        });
    }



    public static HikariDataSource datasource(String uri) { //, String username, String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setMaximumPoolSize(4);
        ds.setJdbcUrl(uri);
        return ds;
    }

    public static void waitFor(long seconds) {
        waitFor(seconds, () -> "");
    }

    public static void waitFor(long seconds, Supplier<String> info) {
        long s = seconds;
        while(s > 0) {
            LOG.warn("...{} {}", s, info.get());
            try {
                Thread.sleep(1000L);
            } catch (Exception e) {

            }
            s = s - 1;
        }
    }
}
