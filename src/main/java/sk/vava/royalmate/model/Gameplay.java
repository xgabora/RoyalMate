package sk.vava.royalmate.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Blob; // Import Blob if directly using (though DAO handles it)
import java.sql.Timestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Gameplay {
    private long id;
    private int accountId;
    private int gameId;
    private BigDecimal stakeAmount;
    private String outcome;
    private BigDecimal payoutAmount;
    private Timestamp timestamp;

    // Transient fields populated by JOINs
    private transient String username;
    private transient String gameName;
    private transient BigDecimal multiplier;
    private transient byte[] coverImageData; // <-- ADDED
}