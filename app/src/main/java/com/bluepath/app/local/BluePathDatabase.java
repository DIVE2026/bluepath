package com.bluepath.app.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {LearningRecord.class}, version = 2, exportSchema = false)
public abstract class BluePathDatabase extends RoomDatabase {
    private static volatile BluePathDatabase instance;

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE learning_records ADD COLUMN clientRecordId TEXT");
            database.execSQL("ALTER TABLE learning_records ADD COLUMN accountId TEXT");
            database.execSQL("UPDATE learning_records SET clientRecordId = printf('00000000-0000-4000-8000-%012d', id) WHERE clientRecordId IS NULL OR clientRecordId = ''");
            database.execSQL("UPDATE learning_records SET accountId = 'guest' WHERE accountId IS NULL");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_learning_records_accountId_clientRecordId ON learning_records(accountId, clientRecordId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_learning_records_accountId_synced ON learning_records(accountId, synced)");
        }
    };

    public abstract LearningRecordDao learningRecordDao();

    public static BluePathDatabase get(Context context) {
        if (instance == null) {
            synchronized (BluePathDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    BluePathDatabase.class,
                                    "bluepath-local.db")
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return instance;
    }
}
