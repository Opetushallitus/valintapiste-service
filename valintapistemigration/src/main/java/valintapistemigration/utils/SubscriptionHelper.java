package valintapistemigration.utils;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class SubscriptionHelper {
    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionHelper.class);

    public static <T> Subscriber<T> subscriber(Consumer<T> tConsumer) {
        return new Subscriber<T>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(T t) {
                tConsumer.accept(t);
            }

            @Override
            public void onError(Throwable t) {
                LOG.error("Mongo subscriber threw exception!", t);
            }

            @Override
            public void onComplete() {

            }
        };
    }
}
