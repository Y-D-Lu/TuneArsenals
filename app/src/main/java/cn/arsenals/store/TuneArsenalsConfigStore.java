package cn.arsenals.store;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

import cn.arsenals.model.TuneArsenalsConfigInfo;
import cn.arsenals.tunearsenals.R;

public class TuneArsenalsConfigStore extends SQLiteOpenHelper {
    private static final int DB_VERSION = 6;
    private final Context context;

    public TuneArsenalsConfigStore(Context context) {
        super(context, "tunearsenals3_config", null, DB_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        try {
            db.execSQL(
                "create table tunearsenals_config3(" +
                    "id text primary key, " + // id
                    "alone_light int default(0), " + // 独立亮度
                    "light int default(-1), " + // 亮度
                    "dis_notice int default(0)," + // 拦截通知
                    "dis_button int default(0)," + // 停用按键
                    "gps_on int default(0)," + // 打开GPS
                    "freeze int default(0)," + // 休眠
                    "screen_orientation int default(-1)," + // 屏幕旋转方向
                    "fg_cgroup_mem text default('')," + // cgroup
                    "bg_cgroup_mem text default('')," + // cgroup
                    "dynamic_boost_mem int default(0)," + //
                    "show_monitor int default(0)" + //
                ")");

            // 初始化默认配置
            String[] gpsOnApps = this.context.getResources().getStringArray(R.array.tunearsenals_gps_on);
            for (String app: gpsOnApps) {
                db.execSQL("insert into tunearsenals_config3(id, gps_on) values (?, ?)", new Object[]{
                    app,
                    1
                });
            }
        } catch (Exception e) {
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            // 屏幕方向
            try {
                db.execSQL("alter table tunearsenals_config3 add column screen_orientation int default(-1)");
            } catch (Exception ignored) {
            }
        }

        if (oldVersion < 4) {
            try {
                db.execSQL("alter table tunearsenals_config3 add column fg_cgroup_mem text default('')");
                db.execSQL("alter table tunearsenals_config3 add column bg_cgroup_mem text default('')");
            } catch (Exception ignored) {
            }
        }

        if (oldVersion < 5) {
            try {
                db.execSQL("alter table tunearsenals_config3 add column dynamic_boost_mem text default(0)");
            } catch (Exception ignored) {
            }
        }

        if (oldVersion < 6) {
            try {
                db.execSQL("alter table tunearsenals_config3 add column show_monitor text default(0)");
            } catch (Exception ignored) {
            }
        }
    }

    public TuneArsenalsConfigInfo getAppConfig(String app) {
        TuneArsenalsConfigInfo tunearsenalsConfigInfo = new TuneArsenalsConfigInfo();
        tunearsenalsConfigInfo.packageName = app;
        try {
            SQLiteDatabase sqLiteDatabase = this.getReadableDatabase();
            Cursor cursor = sqLiteDatabase.rawQuery("select * from tunearsenals_config3 where id = ?", new String[]{app});
            if (cursor.moveToNext()) {
                tunearsenalsConfigInfo.aloneLight = cursor.getInt(cursor.getColumnIndex("alone_light")) == 1;
                tunearsenalsConfigInfo.aloneLightValue = cursor.getInt(cursor.getColumnIndex("light"));
                tunearsenalsConfigInfo.disNotice = cursor.getInt(cursor.getColumnIndex("dis_notice")) == 1;
                tunearsenalsConfigInfo.disButton = cursor.getInt(cursor.getColumnIndex("dis_button")) == 1;
                tunearsenalsConfigInfo.gpsOn = cursor.getInt(cursor.getColumnIndex("gps_on")) == 1;
                tunearsenalsConfigInfo.freeze = cursor.getInt(cursor.getColumnIndex("freeze")) == 1;
                tunearsenalsConfigInfo.screenOrientation = cursor.getInt(cursor.getColumnIndex("screen_orientation"));
                tunearsenalsConfigInfo.fgCGroupMem = cursor.getString(cursor.getColumnIndex("fg_cgroup_mem"));
                tunearsenalsConfigInfo.bgCGroupMem = cursor.getString(cursor.getColumnIndex("bg_cgroup_mem"));
                tunearsenalsConfigInfo.dynamicBoostMem = cursor.getInt(cursor.getColumnIndex("dynamic_boost_mem")) == 1;
                tunearsenalsConfigInfo.showMonitor = cursor.getInt(cursor.getColumnIndex("show_monitor")) == 1;
            }
            cursor.close();
            sqLiteDatabase.close();
        } catch (Exception ignored) {

        }
        return tunearsenalsConfigInfo;
    }

    public boolean setAppConfig(TuneArsenalsConfigInfo tunearsenalsConfigInfo) {
        SQLiteDatabase database = getWritableDatabase();
        getWritableDatabase().beginTransaction();
        try {
            database.execSQL("delete from tunearsenals_config3 where id = ?", new String[]{tunearsenalsConfigInfo.packageName});
            database.execSQL("insert into tunearsenals_config3(id, alone_light, light, dis_notice, dis_button, gps_on, freeze, screen_orientation, fg_cgroup_mem, bg_cgroup_mem, dynamic_boost_mem, show_monitor) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", new Object[]{
                    tunearsenalsConfigInfo.packageName,
                    tunearsenalsConfigInfo.aloneLight ? 1 : 0,
                    tunearsenalsConfigInfo.aloneLightValue,
                    tunearsenalsConfigInfo.disNotice ? 1 : 0,
                    tunearsenalsConfigInfo.disButton ? 1 : 0,
                    tunearsenalsConfigInfo.gpsOn ? 1 : 0,
                    tunearsenalsConfigInfo.freeze ? 1 : 0,
                    tunearsenalsConfigInfo.screenOrientation,
                    tunearsenalsConfigInfo.fgCGroupMem,
                    tunearsenalsConfigInfo.bgCGroupMem,
                    tunearsenalsConfigInfo.dynamicBoostMem ? 1 : 0,
                    tunearsenalsConfigInfo.showMonitor ? 1 : 0
            });
            database.setTransactionSuccessful();
            return true;
        } catch (Exception ex) {
            return false;
        } finally {
            database.endTransaction();
        }
    }

    public boolean resetAll() {
        try {
            SQLiteDatabase database = getWritableDatabase();
            database.execSQL("update tunearsenals_config3 set alone_light = 0, fg_cgroup_mem = '', screen_orientation = ?, bg_cgroup_mem = '', dynamic_boost_mem = 0, show_monitor = 0", new Object[]{
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            });
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean removeAppConfig(String packageName) {
        try {
            SQLiteDatabase database = getWritableDatabase();
            database.execSQL("delete from tunearsenals_config3 where id = ?", new String[]{packageName});
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public ArrayList<String> getFreezeAppList() {
        ArrayList<String> list = new ArrayList<String>();
        try {
            SQLiteDatabase sqLiteDatabase = this.getReadableDatabase();
            Cursor cursor = sqLiteDatabase.rawQuery("select * from tunearsenals_config3 where freeze == 1", null);
            while (cursor.moveToNext()) {
                list.add(cursor.getString(0));
            }
            cursor.close();
            sqLiteDatabase.close();
        } catch (Exception ignored) {
        }
        return list;
    }
}
