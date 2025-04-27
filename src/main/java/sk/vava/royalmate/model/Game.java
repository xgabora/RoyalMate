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
    private int volatility; // Stored as TINYINT UNSIGNED (0-255), used as 1-5 in UI
    private String backgroundColor; // Hex code
    private int createdByAdminId;
    private boolean isActive;
    private Timestamp createdAt;

    // --- Fields populated by JOINs or separate queries ---
    private String createdByAdminUsername; // From joining accounts table
    private long totalSpins;             // From aggregating gameplays table (implement later)
    private byte[] coverImageData;       // From joining game_assets table (convenience)
}