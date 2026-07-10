package com.bluepath.app.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {LearningRecord.class}, version = 1, exportSchema = false)
public abstract class BluePathDatabase extends RoomDatabase {
    private static volatile BluePathDatabase instance;

    public abstract LearningRecordDao learningRecordDao();

    public static BluePathDatabase get(Context context) {
        if (instance == null) {
            synchronized (BluePathDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    BluePathDatabase.class,
                                    "bluepath-local.db")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}
