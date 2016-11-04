package edu.cmu.privacy.privacyfirewall;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Created by YunfanW on 9/30/2016.
 */

public interface DatabaseInterface {
    public Cursor getConnectionCursorByAppId(int appId);
    public boolean insertConnection(int appId, int ruleId, String content, int sensitive);
    public Cursor getAllApplicationCursor();
    public Cursor getApplicationCursorByName(String name);
    public Cursor getApplicationCursorById(int id);
    public boolean insertApplication(String name, String description, int id);
    public Cursor getRuleCursorByAdd(String ipAdd);
    public Cursor getRuleCursorById(int id);
    public boolean updateAction(int id, int action);
    public boolean insertRule(String ipAdd, String ipOwner, int action);
}
