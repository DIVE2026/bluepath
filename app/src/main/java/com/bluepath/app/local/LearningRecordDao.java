package com.bluepath.app.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface LearningRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(LearningRecord record);

    @Update
    void update(LearningRecord record);

    @Query("SELECT * FROM learning_records WHERE accountId = :accountId AND synced = 0 ORDER BY updatedAt ASC")
    List<LearningRecord> unsynced(String accountId);

    @Query("SELECT * FROM learning_records WHERE accountId = :accountId AND (clientRecordId IS NULL OR clientRecordId = '')")
    List<LearningRecord> legacyWithoutUuid(String accountId);

    @Query("UPDATE learning_records SET synced = 1 WHERE accountId = :accountId AND clientRecordId IN (:clientIds)")
    void markSynced(String accountId, List<String> clientIds);

    @Query("UPDATE learning_records SET accountId = :newAccountId WHERE accountId = :oldAccountId")
    void reassignAccount(String oldAccountId, String newAccountId);

    @Query("SELECT COUNT(*) FROM learning_records WHERE accountId = :accountId")
    int countAll(String accountId);

    @Query("SELECT COUNT(*) FROM learning_records WHERE accountId = :accountId AND clientRecordId = :clientRecordId")
    int countClientRecord(String accountId, String clientRecordId);

    @Query("SELECT COUNT(*) FROM learning_records WHERE accountId = :accountId AND recordType = :recordType AND targetId = :targetId AND status = :status AND updatedAt = :updatedAt")
    int countEquivalent(String accountId, String recordType, String targetId, String status, long updatedAt);
}
