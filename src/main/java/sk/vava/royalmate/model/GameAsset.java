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
public class GameAsset {
    private int id;
    private int gameId;
    private AssetType assetType;
    private String assetName;
    private byte[] imageData;
    private BigDecimal symbolPayoutMultiplier; //nepouzivame zatial
    private Timestamp uploadedAt;
}