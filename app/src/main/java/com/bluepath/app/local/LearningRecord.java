package com.bluepath.app.local;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.UUID;

@Entity(
        tableName = "learning_records",
        indices = {
                @Index(value = {"accountId", "clientRecordId"}, unique = true),
                @Index(value = {"accountId", "synced"})
        }
)
public class LearningRecord {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String clientRecordId;
    public String accountId;
    public String recordType;
    public String targetId;
    public String title;
    public String status;
    public long updatedAt;
    public boolean synced;

    public LearningRecord(String accountId, String recordType, String targetId, String title,
                          String status, long updatedAt, boolean synced) {
        this(UUID.randomUUID().toString(), accountId, recordType, targetId, title, status, updatedAt, synced);
    }

    public LearningRecord(String clientRecordId, String accountId, String recordType, String targetId,
                          String title, String status, long updatedAt, boolean synced) {
        this.clientRecordId = clientRecordId == null || clientRecordId.trim().isEmpty()
                ? UUID.randomUUID().toString() : clientRecordId.trim().toLowerCase();
        this.accountId = accountId == null || accountId.trim().isEmpty() ? "guest" : accountId.trim();
        this.recordType = recordType;
        this.targetId = targetId;
        this.title = title;
        this.status = status;
        this.updatedAt = updatedAt;
        this.synced = synced;
    }
}
