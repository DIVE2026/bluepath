package com.bluepath.app.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface LearningRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(LearningRecord record);

    @Query("SELECT * FROM learning_records WHERE synced = 0 ORDER BY updatedAt ASC")
    List<LearningRecord> unsynced();

    @Query("UPDATE learning_records SET synced = 1 WHERE id IN (:ids)")
    void markSynced(List<Long> ids);

    @Query("SELECT COUNT(*) FROM learning_records")
    int countAll();
}
