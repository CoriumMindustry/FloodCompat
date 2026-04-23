package floodcompat;

import arc.*;
import arc.audio.*;
import arc.files.*;
import arc.struct.*;
import arc.util.*;
import mindustry.gen.*;
import mindustry.ui.dialogs.*;

import java.io.*;

import static mindustry.Vars.*;

public class SoundUtils{
    public static Seq<Music> customMusic, vanillaDark, vanillaAmbient, vanillaBoss;
    public static Sound cachedSound;
    public static boolean applied;

    public static void init(){
        Core.app.post(SoundUtils::reloadSound);

        tryLoadMusic();
    }

    public static void replaceVanilla(){
        if(applied || customMusic == null || customMusic.isEmpty()) return;

        vanillaAmbient = control.sound.ambientMusic;
        vanillaDark = control.sound.darkMusic;
        vanillaBoss = control.sound.bossMusic;

        control.sound.ambientMusic = control.sound.darkMusic = control.sound.bossMusic = customMusic;

        applied = true;
    }

    public static void setVanilla(){
        if(!applied) return;

        control.sound.ambientMusic = vanillaAmbient;
        control.sound.darkMusic = vanillaDark;
        control.sound.bossMusic = vanillaBoss;

        vanillaAmbient = vanillaDark = vanillaBoss = null;

        applied = false;
    }

    public static void tryLoadMusic(){
        Fi folder = dataDirectory.child("floodcompat");
        if(!folder.exists()){
            Log.warn("Failed to load custom music...");
            return;
        }

        customMusic = new Seq<>();
        folder.walk(fi -> {
            if(fi.extEquals("mp3") || fi.extEquals("ogg")){
                try{
                    customMusic.add(
                        new Music(fi)
                    );
                }catch(Exception e){
                    Log.warn("Failed to load music: " + fi.name());
                }
            }
        });
    }

    public static void reloadSound(){
        if(Core.settings.getString("fc-wind3-path", "null").equals("null")){
            cachedSound = Sounds.wind3;
            return;
        }

        try{
            cachedSound = new Sound(
                Core.files.absolute(
                    Core.settings.getString("fc-wind3-path", "null")
                )
            );
        }catch(Exception e){
            Core.settings.put("fc-wind3-path", "null");
            cachedSound = Sounds.wind3;
        }
    }

    public static void addSettings(SettingsMenuDialog.SettingsTable t){
        t.checkPref("fc-customs", false, reload -> {
            if(reload) reloadSound();
        });

        t.row().button("@fc-choose", Icon.downloadSmall, () ->
            platform.showMultiFileChooser(file -> {
                try{
                    Sound sound = new Sound(file);
                    sound.play();

                    cachedSound = sound;
                    Core.settings.put("fc-wind3-path", file.path());
                }catch(Exception ex){
                    ui.showErrorMessage("@fc-file-error");
                    cachedSound = Sounds.wind3;
                    Core.settings.put("fc-wind3-path", "null");
                }
            }, "mp3", "ogg")
        ).width(240f);

        if(mobile) return;
        t.row().button("@fc-open", Icon.linkSmall,
            () -> {
                Fi folder = dataDirectory.child("floodcompat");
                if(!folder.exists()){
                    folder.mkdirs();

                    try{
                        folder.child("readme.txt").file().createNewFile();
                        var fw = folder.child("readme.txt").writer(false);

                        fw.write(
                            "Place custom music files (.mp3 / .ogg) in this folder\nThe music will be loaded when the game starts and will play instead of the default music when playing Flood\n\nNote that music added mid-game won't play until the game is restarted"
                        );
                        fw.close();
                    }catch(IOException ignored){}
                }

                Core.app.openFolder(dataDirectory.child("floodcompat").absolutePath());
            }
        ).width(240f);
    }
}
