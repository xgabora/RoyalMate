package sk.vava.royalmate.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {
    private int id;
    private String username;
    private String passwordHash;
    private String email;
    private BigDecimal balance;
    private String profilePictureColor;
    private boolean isAdmin;
    private Timestamp createdAt;
    private Timestamp lastLoginAt;
    private Timestamp lastWofSpinAt;
}