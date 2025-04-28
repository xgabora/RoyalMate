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
public class Gameplay {
    private long id; // Corresponds to BIGINT AUTO_INCREMENT PRIMARY KEY
    private int accountId;
    private int gameId;
    private BigDecimal stakeAmount;
    private String outcome; // Store result grid/symbols as String (e.g., JSON) or just descriptive text
    private BigDecimal payoutAmount;
    private Timestamp timestamp;

    // Transient field populated by JOIN for leaderboard display
    private transient String username;
}