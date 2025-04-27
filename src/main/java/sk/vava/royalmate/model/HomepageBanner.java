package sk.vava.royalmate.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomepageBanner {

    private int id;
    private String name;
    private byte[] imageData; // Store image data as byte array
    private int position; // Use int for TINYINT UNSIGNED
    private boolean isActive;
    private int uploadedByAdminId;
    private Timestamp uploadedAt;

    // Optional: Add reference to the uploading Admin Account object if needed later
    // private Account uploadedByAdmin;
}