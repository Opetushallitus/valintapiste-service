package valintapistemigration.utils;

import com.google.common.math.Quantiles;

import java.util.*;

public class QueueUtil {


    public static <T> Map.Entry<Long, List<T>> gatherRowsUntilTargetBatchSize(Queue<List<T>> queue, int targetBatchSize) {

        List<T> data = new ArrayList<>();
        Set<Integer> medianSizes = new HashSet<>();
        long hakemuksiaLuettu = 0;
        do {

            List<T> poll = queue.poll();
            if(poll == null) {
                return new AbstractMap.SimpleImmutableEntry(hakemuksiaLuettu, data);
            } else {
                medianSizes.add(poll.size());
                data.addAll(poll);
                hakemuksiaLuettu = hakemuksiaLuettu + 1;
            }
            int expectedSizeOfNextBatch = (int)Quantiles.median().compute(medianSizes);
            if(targetBatchSize < data.size() + expectedSizeOfNextBatch) {
                return new AbstractMap.SimpleImmutableEntry(hakemuksiaLuettu, data);
            } else {
                // continue
            }
        } while(true);


    }
}
