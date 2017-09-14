package valintapistemigration;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DocumentToPisteet {
    private static final Logger LOG = LoggerFactory.getLogger(DocumentToPisteet.class);
    @Deprecated
    public static class Piste {
        public final String tunniste;
        public final String osallistuminen;
        public final Optional<String> arvo;
        public Piste(String tunniste, String osallistuminen, Optional<String> arvo) {
            this.tunniste = tunniste;
            this.osallistuminen = osallistuminen;
            this.arvo = arvo;
        }
    }
    @Deprecated
    public static class Hakemus {
        public final String hakemusOid;
        public final List<Piste> pisteet;
        public Hakemus(String hakemusOid, List<Piste> pisteet) {
            this.hakemusOid = hakemusOid;
            this.pisteet = pisteet;
        }
    }
    private static BiFunction<Map<String, Object>, String, List<ValintapisteDAO.PisteRow>> ADDITIONAL_INFO_TO_ROWS = (additionalInfo, oid) -> {
        List<ValintapisteDAO.PisteRow> pisteet = additionalInfo.entrySet().stream().flatMap(entry -> {
            String k = entry.getKey();
            Object v = entry.getValue();
            if (k.endsWith("-OSALLISTUMINEN")) {
                String tunniste = k.replaceAll("-OSALLISTUMINEN", "");
                String osallistuminen = v.toString();
                if(!"MERKITSEMATTA".equals(osallistuminen)) {
                    return Stream.of(new ValintapisteDAO.PisteRow(oid, tunniste,(String)additionalInfo.get(tunniste), osallistuminen));
                } else {
                    return Stream.empty();
                }
            } else {
                return Stream.empty();
            }

        }).collect(Collectors.toList());
        return pisteet;
    };

    public static Optional<List<ValintapisteDAO.PisteRow>> documentToRows(Document document) {
        String oid = (String)document.get("oid");
        if(oid == null) {
            LOG.error("Hakemus without hakemusOID!");
            return Optional.empty();
        }
        Map<String, Object> additionalInfo = (Map<String, Object>)document.get("additionalInfo");
        boolean containsAdditionalInfo = additionalInfo != null;
        if(containsAdditionalInfo) {
            List<ValintapisteDAO.PisteRow> rows = ADDITIONAL_INFO_TO_ROWS.apply(additionalInfo, oid);

            return Optional.of(rows).filter(r -> !r.isEmpty());
        }
        return Optional.empty();
    }

    public static Optional<Hakemus> documentToHakemus(Document document) {
        String oid = (String)document.get("oid");
        if(oid == null) {
            LOG.error("Hakemus without hakemusOID!");
            return Optional.empty();
        }
        boolean containsAdditionalInfo = document.get("additionalInfo") != null;
        if(containsAdditionalInfo) {
            List<Piste> pisteet = documentToPisteet(document);
            if(!pisteet.isEmpty()) {
                return Optional.of(new Hakemus(oid, pisteet));
            }
        }
        return Optional.empty();
    }

    public static List<Piste> documentToPisteet(Document document) {
        Map<String, Object> additionalInfo = (Map<String, Object>)document.get("additionalInfo");
        List<Piste> pisteet = additionalInfo.entrySet().stream().flatMap(entry -> {
            String k = entry.getKey();
            Object v = entry.getValue();
            if (k.endsWith("-OSALLISTUMINEN")) {
                String tunniste = k.replaceAll("-OSALLISTUMINEN", "");
                String osallistuminen = v.toString();
                if(!"MERKITSEMATTA".equals(osallistuminen)) {
                    Optional<String> arvoMaybe = Optional.ofNullable((String) additionalInfo.get(tunniste));
                    return Stream.of(new Piste(tunniste, osallistuminen, arvoMaybe));
                } else {
                    return Stream.empty();
                }
            } else {
                return Stream.empty();
            }

        }).collect(Collectors.toList());
        return pisteet;
    }
}
