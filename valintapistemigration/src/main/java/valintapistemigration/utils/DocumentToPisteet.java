package valintapistemigration.utils;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DocumentToPisteet {
    private static final Logger LOG = LoggerFactory.getLogger(DocumentToPisteet.class);

    private static BiFunction<Map<String, Object>, String, List<ValintapisteDAO.PisteRow>> ADDITIONAL_INFO_TO_ROWS = (additionalInfo, oid) -> {
        List<ValintapisteDAO.PisteRow> pisteet = additionalInfo.entrySet().stream().flatMap(entry -> {
            String k = entry.getKey();
            Object v = entry.getValue();
            if (k.endsWith("-OSALLISTUMINEN")) {
                String tunniste = k.replaceAll("-OSALLISTUMINEN", "");
                String osallistuminen = v.toString();
                return Stream.of(new ValintapisteDAO.PisteRow(oid, tunniste,(String)additionalInfo.get(tunniste), osallistuminen));
            } else {
                return Stream.empty();
            }

        }).collect(Collectors.toList());
        return pisteet;
    };

    public static List<ValintapisteDAO.PisteRow> documentToRows(Document document) {
        String oid = (String)document.get("oid");
        if(oid == null) {
            LOG.error("Hakemus without hakemusOID!");
            return Collections.emptyList();
        }
        Map<String, Object> additionalInfo = (Map<String, Object>)document.get("additionalInfo");
        boolean containsAdditionalInfo = additionalInfo != null;
        if(containsAdditionalInfo) {
            List<ValintapisteDAO.PisteRow> rows = ADDITIONAL_INFO_TO_ROWS.apply(additionalInfo, oid);

            return rows;
        }
        return Collections.emptyList();
    }

}
