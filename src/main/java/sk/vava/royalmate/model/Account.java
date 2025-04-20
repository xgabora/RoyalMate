package sk.vava.royalmate.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data // Generates getters, setters, toString, equals, hashCode
@Builder // Provides builder pattern for object creation
@NoArgsConstructor // Generates no-args constructor
@AllArgsConstructor // Generates all-args constructor
public class Account {
    private int id;
    private String username;
    private String passwordHash; // Store the hash, not the plain password
    private String email;
    private BigDecimal balance;
    private String profilePictureColor; // Hex color code e.g., "#CCCCCC"
    private boolean isAdmin;
    private Timestamp createdAt;
    private Timestamp lastLoginAt;
}