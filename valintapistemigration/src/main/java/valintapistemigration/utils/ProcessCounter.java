package valintapistemigration.utils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.concurrent.atomic.AtomicLong;

public class ProcessCounter {
    private static final AtomicLong hakemuksiaMongossa = new AtomicLong(-1);
    private static final AtomicLong hakemuksiaKäsitelty = new AtomicLong(0);
    private static final AtomicLong hakemuksiaLuettuMongosta = new AtomicLong(0);
    private static final AtomicLong epäonnistuneitaHakemuksenKäsittelyitä = new AtomicLong(0);

    public void setHakemuksiaMongossa(long count) {
        hakemuksiaMongossa.set(count);
    }

    public void hakemusKäsitelty(long määrä) {
        hakemuksiaKäsitelty.addAndGet(määrä);
    }

    public void hakemuksenKäsittelyEpäonnistui(long määrä) {
        epäonnistuneitaHakemuksenKäsittelyitä.addAndGet(määrä);
    }

    public void hakemusLuettuMongosta() {
        hakemuksiaLuettuMongosta.incrementAndGet();
    }

    @Override
    public String toString() {
        long overall = hakemuksiaMongossa.get();
        if(overall == -1L) {
            return "";
        } else {
            long i = hakemuksiaLuettuMongosta.get();
            long done = hakemuksiaKäsitelty.get();
            long failed = epäonnistuneitaHakemuksenKäsittelyitä.get();
            return String.format("READ FROM MONGO %s! HANDLED %s! FAILED %s!",
                    formatAsPercentage(i, overall), formatAsPercentage(done, overall), formatAsPercentage(failed, overall));
        }
    }

    private String formatAsPercentage(long a, long b) {
        return String.format("%s / %s which is %s%%", a, b, new BigDecimal((((double) a) / ((double) b)) * 100d, new MathContext(2, RoundingMode.HALF_EVEN)).toString());
    }

}
