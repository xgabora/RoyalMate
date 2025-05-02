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
    private byte[] imageData;
    private int position;
    private boolean isActive;
    private int uploadedByAdminId;
    private Timestamp uploadedAt;
}