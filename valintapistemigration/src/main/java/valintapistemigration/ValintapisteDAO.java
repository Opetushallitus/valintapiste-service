package valintapistemigration;

import com.google.common.collect.Lists;
import org.dalesbred.Database;

import java.util.List;
import java.util.stream.Collectors;

public class ValintapisteDAO {
    private final Database db;
    private final String tallettaja;

    public static class PisteRow {
        public final String hakemusOID;
        public final String tunniste;
        public final String arvo;
        public final String osallistuminen;
        //public final String tallettaja;
        public PisteRow(String hakemusOID, String tunniste, String arvo, String osallistuminen) {
            this.hakemusOID = hakemusOID;
            this.tunniste = tunniste;
            this.arvo = arvo;
            this.osallistuminen = osallistuminen;
            //this.tallettaja = tallettaja;
        }
    }

    public ValintapisteDAO(String tallettaja, Database db) {
        this.db = db;
        this.tallettaja = tallettaja;
    }

    public void insertBatch(List<PisteRow> rows) {
        db.updateBatch("INSERT INTO valintapiste (arvo, hakemus_oid, tunniste, osallistuminen, tallettaja) VALUES (?,?,?,?::osallistumistieto,?)",
                rows.stream().map(row -> Lists.newArrayList(row.arvo, row.hakemusOID, row.tunniste, row.osallistuminen, tallettaja)).collect(Collectors.toList()));
    }

}
