package eu.faircode.netguard;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;


import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "NetGuard.Database";

    private static final String DB_NAME = "Netguard";
    private static final int DB_VERSION = 2;

    private static List<LogChangedListener> logChangedListeners = new ArrayList<LogChangedListener>();

    private Context mContext;

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.i(TAG, "Creating database " + DB_NAME + ":" + DB_VERSION);
        createTableLog(db);
    }

    private void createTableLog(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE log (" +
                " ID INTEGER PRIMARY KEY AUTOINCREMENT" +
                ", time INTEGER NOT NULL" +
                ", version INTEGER NULL" +
                ", ip TEXT" +
                ", protocol INTEGER NULL" +
                ", uid INTEGER NULL" +
                ");");
        db.execSQL("CREATE INDEX idx_log_time ON log(time)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.i(TAG, "Upgrading from version " + oldVersion + " to " + newVersion);

        db.beginTransaction();
        try {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE log ADD COLUMN version INTEGER NULL");
                db.execSQL("ALTER TABLE log ADD COLUMN protocol INTEGER NULL");
                db.execSQL("ALTER TABLE log ADD COLUMN uid INTEGER NULL");
                oldVersion = 2;
            }

            db.setVersion(DB_VERSION);

            db.setTransactionSuccessful();
        } catch (Throwable ex) {
            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
        } finally {
            db.endTransaction();
        }
    }

    // Location

    public DatabaseHelper insertLog(int version, String ip, int protocol, int uid) {
        synchronized (mContext.getApplicationContext()) {
            SQLiteDatabase db = this.getWritableDatabase();

            ContentValues cv = new ContentValues();
            cv.put("time", new Date().getTime());
            cv.put("version", version);
            cv.put("ip", ip);

            if (protocol < 0)
                cv.putNull("protocol");
            else
                cv.put("protocol", protocol);

            if (uid < 0)
                cv.putNull("uid");
            else
                cv.put("uid", uid);

            if (db.insert("log", null, cv) == -1)
                Log.e(TAG, "Insert log failed");
        }

        for (LogChangedListener listener : logChangedListeners)
            try {
                listener.onChanged();
            } catch (Throwable ex) {
                Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
            }

        return this;
    }

    public DatabaseHelper clear() {
        synchronized (mContext.getApplicationContext()) {
            SQLiteDatabase db = this.getReadableDatabase();
            db.delete("log", null, new String[]{});
            db.execSQL("VACUUM");
        }

        for (LogChangedListener listener : logChangedListeners)
            try {
                listener.onChanged();
            } catch (Throwable ex) {
                Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
            }

        return this;
    }

    public Cursor getLog() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT ID AS _id, * FROM log";
        query += " ORDER BY time DESC";
        return db.rawQuery(query, new String[]{});
    }

    public static void addLogChangedListener(LogChangedListener listener) {
        logChangedListeners.add(listener);
    }

    public static void removeLocationChangedListener(LogChangedListener listener) {
        logChangedListeners.remove(listener);
    }

    public interface LogChangedListener {
        void onChanged();
    }
}
