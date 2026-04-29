package floodcompat;

import arc.*;
import arc.audio.*;
import arc.files.*;
import arc.func.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.gen.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import java.io.*;

import static mindustry.Vars.*;

public class SoundUtils{
    public static Seq<Music> customMusic, vanillaDark, vanillaAmbient, vanillaBoss;
    public static MobileUI mobileUI;
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
        if(!folder.exists()) return;

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

        t.getSettings().add(
            new ButtonTable(tb -> {
                tb.row().button("@fc-choose", Icon.downloadSmall, () ->
                    platform.showMultiFileChooser(file -> {
                        try{
                            Sound sound = new Sound(file);

                            if(sound.getLength() >= 20){
                                ui.showConfirm("@fc-sound-warning", () -> {
                                    cachedSound = sound;
                                    Core.settings.put("fc-wind3-path", file.path());
                                });
                            }else{
                                sound.play();

                                cachedSound = sound;
                                Core.settings.put("fc-wind3-path", file.path());
                            }
                        }catch(Exception ex){
                            ui.showErrorMessage("@fc-file-error");
                            cachedSound = Sounds.wind3;
                            Core.settings.put("fc-wind3-path", "null");
                        }
                    }, "mp3", "ogg")
                ).width(240f);

                tb.row().button("@fc-open", Icon.linkSmall, () -> {
                    Fi folder = dataDirectory.child("floodcompat");
                    if(!folder.exists()){
                        folder.mkdirs();

                        if(!mobile){
                            try{
                                folder.child("readme.txt").file().createNewFile();
                                var fw = folder.child("readme.txt").writer(false);

                                fw.write(
                                    "Place custom music files (.mp3 / .ogg) in this folder\nThe music will be loaded when the game starts and will play instead of the default music when playing Flood\n\nNote that music added mid-game won't play until the game is restarted"
                                );
                                fw.close();
                            }catch(IOException ignored){
                            }
                        }
                    }

                    if(mobile){
                        openUI();
                        return;
                    }

                    Core.app.openFolder(dataDirectory.child("floodcompat").absolutePath());
                }).width(240f);

                tb.row();
            })
        );
        t.rebuild();
    }

    public static void openUI(){
        if(mobileUI == null)
            mobileUI = new MobileUI();
        mobileUI.show();
    }

    // a hack that lets us have buttons in the settings - doing table.button without this would have them be removed every rebuild()
    public static class ButtonTable extends SettingsMenuDialog.SettingsTable.Setting{
        public Cons<SettingsMenuDialog.SettingsTable> builder;

        public ButtonTable(Cons<SettingsMenuDialog.SettingsTable> builder){
            super(null);

            this.builder = builder;
        }

        public void add(SettingsMenuDialog.SettingsTable t){
            builder.get(t);
        }
    }

    public static class MobileUI{
        public BaseDialog dialog = new BaseDialog("@fc-music-ui");
        public Table table = new Table();
        public Cell<ScrollPane> canvas;

        public MobileUI(){
            dialog.addCloseButton();
            dialog.fill(sub ->
                sub.center().top().marginTop(40f).marginBottom(90f).table(entry ->
                    canvas = entry.pane(table).size(entry.getWidth(), entry.getWidth()).scrollX(false).growX()
                )
            );
        }

        public void show(){
            rebuild();
            dialog.show();
        }

        public static final float
        size = 60f,
        iconSize = size * 0.85f,
        iconSizeSmall = size * 0.5f,
        iconSizeTiny = size * 0.33f,
        textWidth = 35f,
        charWidth = 10f;

        static float scaledSize(String text){
            return textWidth + (charWidth * text.length());
        }

        public void rebuild(){
            table.reset();
            table.setWidth(Core.graphics.getWidth());

            Seq<Fi> files = dataDirectory.child("floodcompat").findAll();
            if(!files.isEmpty()){
                for(Fi file : files){
                    table.table(Tex.button, s -> {
                        s.table(Tex.underline, e -> {
                            ImageButton img = new ImageButton(Icon.trash, Styles.flati);
                            img.resizeImage(iconSizeTiny);
                            img.clicked(() ->
                                ui.showConfirm("@fc-music-confirm", () -> {
                                    removeFile(file);
                                    rebuild();
                                })
                            );

                            e.add(img).size(iconSizeSmall).touchable(Touchable.enabled).scaling(Scaling.bounded).padBottom(2f);
                        }).growX().row();
                        s.table(u -> {
                            String text = Strings.format("@ @", Iconc.pencil, file.nameWithoutExtension());

                            ImageButton img = new ImageButton(Icon.fileImage, Styles.flati);
                            img.resizeImage(iconSize);
                            img.left();
                            img.labelWrap(" " + text);
                            img.clicked(() ->
                                ui.showConfirm("@fc-music-confirm", () -> {
                                    removeFile(file);
                                    rebuild();
                                })
                            );

                            u.add(img).scaling(Scaling.bounded).size(iconSize + scaledSize(text), iconSize).padTop(2f);
                        }).growX().row();
                    }).pad(1.5f).grow().width(canvas.maxWidth());
                }
            }else table.table(tb -> tb.label(() -> "@empty").growX().center().style(Styles.outlineLabel).pad(20f)).width(canvas.maxWidth()).row();

            String text = Core.bundle.get("fc-open");
            table.button(text, Icon.units, mobileUI::addFile).minWidth(scaledSize(text)).grow();
        }

        public void addFile(){
            platform.showMultiFileChooser(file -> {
                try{
                    Music music = new Music(file);

                    customMusic.add(music);
                    file.copyTo(
                        dataDirectory.child("floodcompat")
                    );

                    rebuild();
                }catch(Exception ex){
                    ui.showErrorMessage("@fc-file-error");
                }
            }, "mp3", "ogg");
        }

        public void removeFile(Fi file){
            file.delete();
            customMusic.remove(m -> {
                try{
                    Fi mfile = Reflect.get(Music.class, m, "file");
                    return mfile == file;
                }catch(Exception ignored){
                    return false;
                }
            });

            rebuild();
        }
    }
}
