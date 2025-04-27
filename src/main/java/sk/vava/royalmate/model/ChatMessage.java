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
public class ChatMessage {

    private long id; // Use long for BIGINT
    private int senderId;
    private String messageText;
    private Timestamp sentAt;

    // Optional: Add reference to the sender Account object if needed later
    // private Account sender;
}