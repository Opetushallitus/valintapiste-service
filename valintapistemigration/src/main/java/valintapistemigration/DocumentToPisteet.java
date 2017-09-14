package valintapistemigration;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DocumentToPisteet {
    private static final Logger LOG = LoggerFactory.getLogger(DocumentToPisteet.class);

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
    public static class Hakemus {
        public final String hakemusOid;
        public final List<Piste> pisteet;
        public Hakemus(String hakemusOid, List<Piste> pisteet) {
            this.hakemusOid = hakemusOid;
            this.pisteet = pisteet;
        }
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
