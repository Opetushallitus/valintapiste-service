package valintapistemigration;

import com.google.common.math.Quantiles;

import java.util.*;

public class QueueUtil {


    public static <T> List<T> gatherRowsUntilTargetBatchSize(Queue<List<T>> queue, int targetBatchSize) {
        List<T> data = new ArrayList<>();
        Set<Integer> medianSizes = new HashSet<>();
        do {

            List<T> poll = queue.poll();
            if(poll == null) {
                return data;
            } else {
                medianSizes.add(poll.size());
                data.addAll(poll);
            }
            int expectedSizeOfNextBatch = (int)Quantiles.median().compute(medianSizes);
            if(targetBatchSize < data.size() + expectedSizeOfNextBatch) {
                return data;
            } else {
                // continue
            }
        } while(true);


    }
}
