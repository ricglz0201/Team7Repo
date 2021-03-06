package codeu.model.store.basic;
import codeu.model.data.Setting;

import java.util.ArrayList;
import java.util.List;
import codeu.model.store.persistence.PersistentStorageAgent;

public class SettingStore {
    /**
     * Singleton instance of UserStore.
     */
    private static SettingStore instance;

    /**
     * Returns the singleton instance of UserStore that should be shared between all servlet classes.
     * Do not call this function from a test; use getTestInstance() instead.
     */
    public static SettingStore getInstance() {
        if (instance == null) {
            instance = new SettingStore(PersistentStorageAgent.getInstance());
        }
        return instance;
    }

    /**
     * The PersistentStorageAgent responsible for loading Setting from and saving Users to Datastore.
     */

    private PersistentStorageAgent persistentStorageAgent;

    //** The in list memory of the setting. */
    private List<Setting > settings;
    /**
     * This class is a singleton, so its constructor is private. Call getInstance() instead.
     */
    private SettingStore(PersistentStorageAgent persistentStorageAgent) {
        this.persistentStorageAgent = persistentStorageAgent;
        settings = new ArrayList<>();
    }
    //
    public void updateSetting(Setting setting) {
        persistentStorageAgent.writeThrough(setting);
    }

    public List<Setting> getallSettings(){
        return  new ArrayList<>(settings);
    }

    public Setting getSettingbyType(Setting.SettingType type){
        for(Setting setting: settings){
            if(setting.getType().equals(type)){
                return setting;
            }
        }
        return null;
    }
    public void setSettings(List<Setting> settings) {
        this.settings = settings;
    }

}
