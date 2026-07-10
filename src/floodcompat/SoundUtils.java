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
    public static Seq<Music> customMusic = new Seq<>(), vanillaDark, vanillaAmbient, vanillaBoss;
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

        customMusic.clear();
        folder.walk(fi -> {
            if(fi.extEquals("mp3") || fi.extEquals("ogg") || (mobile && fi.extEquals("mus"))){
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

    private static final FileChooser.FileChooserParams params = FileChooser.open("mp3", "ogg");
    public static void addSettings(SettingsMenuDialog.SettingsTable t){
        t.checkPref("fc-customs", false, reload -> {
            if(reload) reloadSound();
        });

        t.getSettings().add(
            new ButtonTable(tb -> {
                tb.row().button("@fc-choose", Icon.downloadSmall, () ->
                    params.submit(file -> {
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
                    })
                ).width(240f);

                tb.row().button("@fc-open", Icon.linkSmall, () -> {
                    Fi folder = dataDirectory.child("floodcompat");
                    if(!folder.exists()){
                        folder.mkdirs();

                        if(!mobile){
                            try{
                                var fw = folder.child("readme.txt").writer(false);

                                fw.write(
                                    "Place custom music files (.mp3 / .ogg) in this folder\nThe music will be loaded when the game starts and will play instead of the default music when playing Flood\n\nNote that music added mid-game won't play until the game is restarted or the reload button is pressed"
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

                if(!mobile){ // unnecessary on mobile - it already adds/removes music on the fly
                    tb.row().button("@fc-music-reload", Icon.rotateSmall, () -> {
                        customMusic.each(Music::stop);
                        tryLoadMusic();
                    }).width(240f);
                }

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
                            img.resizeImage(iconSizeSmall);
                            img.clicked(() ->
                                ui.showConfirm("@fc-music-confirm", () -> {
                                    removeFile(file);
                                    rebuild();
                                })
                            );

                            e.add(img).size(iconSizeSmall).touchable(Touchable.enabled).scaling(Scaling.bounded).padBottom(2f);
                        }).growX().row();
                        s.table(u -> {
                            String text = Strings.format("@ @", Iconc.cancel, file.nameWithoutExtension());

                            ImageButton img = new ImageButton(Icon.file, Styles.flati);
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

                    table.row();
                }
            }else table.table(tb -> tb.label(() -> "@empty").growX().center().style(Styles.outlineLabel).pad(20f)).width(canvas.maxWidth()).row();

            String text = Core.bundle.get("fc-open");
            table.button(text, Icon.download, mobileUI::addFile).minWidth(scaledSize(text)).grow();
        }

        public void addFile(){
            Fi folder = dataDirectory.child("floodcompat");
            if(!folder.exists())
                folder.mkdirs();

            params.submit(file -> {
                if(Strings.canParsePositiveInt(file.name().substring(file.name().lastIndexOf(":") + 1))){
                    ui.showTextInput("@fc-music-rename", "@fc-music-warning", "", s ->
                        load(folder, file, s)
                    );
                    return;
                }

                load(folder, file, "");
            });
        }

        public void load(Fi folder, Fi file, String string){
            try{
                new Music(file); // test whether this is a valid music file

                String in = string.isEmpty() ? file.name() : string;
                String ext = file.extension().isEmpty() ? ".mus" : '.' + file.extension();
                Fi out = folder.child(in.replaceAll("[^a-zA-Z0-9]", "") + ext);

                file.copyTo(out);
                customMusic.add(
                    new Music(
                        out
                    )
                );

                rebuild();
            }catch(Exception ex){
                ui.showException("@fc-file-error", ex);
            }
        }

        public void removeFile(Fi file){
            Music music = customMusic.find(m -> {
                try{
                    Fi f = Reflect.get(m, "file");
                    return f == null || !f.exists() || f.path().equals(file.path()) || f.name().equals(file.name());
                }catch(Exception ignored){
                    return false;
                }
            });
            file.delete();

            if(music != null){
                customMusic.remove(music);
                music.stop();
            }

            rebuild();
        }
    }
}
