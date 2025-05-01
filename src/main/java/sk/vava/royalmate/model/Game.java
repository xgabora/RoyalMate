package sk.vava.royalmate.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Game {
    private int id;
    private String name;
    private String description;
    private GameType gameType;
    private BigDecimal minStake;
    private BigDecimal maxStake;
    private int volatility;
    private String backgroundColor;
    private int createdByAdminId;
    private boolean isActive;
    private Timestamp createdAt;

    // --- Fields populated by JOINs or separate queries ---
    private String createdByAdminUsername;
    private long totalSpins;
    private byte[] coverImageData;
    private BigDecimal maxPayout; // <-- ADDED
}