package sk.vava.royalmate.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatistics {
    private long totalSpins;
    private BigDecimal totalWagered;
    private BigDecimal totalWon;
    private long distinctGamesPlayed;
}