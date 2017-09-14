package valintapistemigration;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.zaxxer.hikari.HikariDataSource;
import org.bson.Document;
import org.dalesbred.Database;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
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
import static valintapistemigration.SubscriptionHelper.*;
import static valintapistemigration.SubscriptionHelper.subscriber;

public class Valintapistemigration {
    private static final Logger LOG = LoggerFactory.getLogger(Valintapistemigration.class);
    private static final AtomicInteger NUMBER_OF_HAKEMUKSIA_MONGOSTA = new AtomicInteger(0);
    private static final AtomicInteger NUMBER_OF_HAKEMUKSIA_WITH_PISTEET = new AtomicInteger(0);
    private static final AtomicLong NUMBER_OF_HAKEMUKSIA_IN_MONGO = new AtomicLong(-1);
    private static final AtomicInteger NUMBER_OF_PISTEET_STORED_TO_POSTGRE = new AtomicInteger(0);
    private static final AtomicInteger NUMBER_OF_HAKEMUKSIA_STORED_TO_POSTGRE = new AtomicInteger(0);
    private static final String TALLENTAJA = "migraatio";

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
        final ArrayBlockingQueue<Hakemus> queue = new ArrayBlockingQueue<>(1038346);
        final Database postgresql = Database.forDataSource(datasource);

        Runnable storeDocumentToPostgresql = () -> {
            try {
                LOG.info("Running PostgreSQL update! Taking from queue (size = {})", queue.size());
                Hakemus hakemus = queue.take();
                hakemus.pisteet.forEach(piste -> {
                    LOG.debug("INSERTING {} {} {} {} {}", hakemus.hakemusOid, piste.tunniste, piste.arvo.orElse(null), piste.osallistuminen, TALLENTAJA);
                    if(piste.arvo.isPresent()) {
                        piste.arvo.ifPresent(arvo -> {
                            postgresql.update("insert into valintapiste (hakemus_oid, tunniste, arvo, osallistuminen, tallettaja) VALUES (?,?,?,?::osallistumistieto,?)", hakemus.hakemusOid, piste.tunniste, piste.arvo.get(), piste.osallistuminen, TALLENTAJA);
                        });
                    } else {
                        postgresql.update("insert into valintapiste (hakemus_oid, tunniste, osallistuminen, tallettaja) VALUES (?,?,?::osallistumistieto,?)", hakemus.hakemusOid, piste.tunniste,  piste.osallistuminen, TALLENTAJA);
                    }
                    NUMBER_OF_PISTEET_STORED_TO_POSTGRE.incrementAndGet();
                });
                NUMBER_OF_HAKEMUKSIA_STORED_TO_POSTGRE.incrementAndGet();
            } catch (Throwable e) {
                e.printStackTrace();
                System.out.println(e.getMessage());
            } finally {
                LOG.info("DONE INSERTING TO POSTGRESQL!");
            }
        };

        MongoDatabase database = mongoClient.getDatabase("hakulomake");
        MongoCollection<Document> collection = database.getCollection("application");

        collection.count().subscribe(subscriber(count -> NUMBER_OF_HAKEMUKSIA_IN_MONGO.set(count)));

        Consumer<Document> handleDocument = (document) -> {
            int i = NUMBER_OF_HAKEMUKSIA_MONGOSTA.incrementAndGet();
            long overall = NUMBER_OF_HAKEMUKSIA_IN_MONGO.get();
            LOG.info("PROCESSED! {} / {} which is {}%", i, overall, new BigDecimal((((double)i)/((double)overall)) * 100d, new MathContext(2, RoundingMode.HALF_EVEN)).toString());
            Optional<Hakemus> hakemus = documentToHakemus(document);
            hakemus.ifPresent(h -> {
                NUMBER_OF_HAKEMUKSIA_WITH_PISTEET.incrementAndGet();
                LOG.info("Adding to queue! (size = {})", queue.size());
                queue.add(h);
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
        //ds.setUsername(username);
        //ds.setPassword(password);
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
