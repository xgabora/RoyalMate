package sk.vava.royalmate.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Blob;
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

    private transient String username;
    private transient String gameName;
    private transient BigDecimal multiplier;
    private transient byte[] coverImageData;
}