package floodcompat;

import arc.*;
import arc.audio.*;
import arc.files.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.input.*;
import arc.math.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.mod.*;
import mindustry.type.*;
import mindustry.ui.dialogs.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.*;

import java.util.Objects;

import static arc.math.Angles.*;
import static arc.graphics.g2d.Draw.*;
import static mindustry.Vars.*;

import static floodcompat.SettingCache.*;

public class EditDrawers{
    public static class Data{
        final Effect effect;
        final Color color;
        final int rgba;

        public Data(float r, float g, float b, float a){
            color = new Color(r, g, b, a);
            rgba = color.rgba();

            effect = new Effect(25, e -> {
                color(color);
                randLenVectors(e.id, e.fin(), Math.min(2, Mathf.round(12 * color.a)), 12f, (x, y, fin, fout) -> {
                    alpha((0.5f - Math.abs(fin - 0.5f)) * 2f);
                    Fill.circle(e.x + x, e.y + y, 3f + fout * 4f);
                });
            });
        }

        public void reset(){
            color.set(rgba);
        }
    }

    public static class PalCache{
        final String from;
        final int[] colors;

        public PalCache(Player from, int[] colors){
            this.from = from.coloredName();
            this.colors = colors;
        }
    }

    public static final Seq<Data> dataMap = Seq.with(
        new Data(0.518f, 0.725f, 0.82f, 0.69f),
        new Data(0.388f, 0.682f, 0.82f, 0.75f),
        new Data(0.286f, 0.616f, 0.769f, 0.75f),
        new Data(0.208f, 0.573f, 0.741f, 0.75f),
        new Data(0.153f, 0.525f, 0.702f, 0.8f),
        new Data(0.106f, 0.478f, 0.651f, 0.8f),
        new Data(0.059f, 0.435f, 0.612f, 0.8f),
        new Data(0.035f, 0.4f, 0.569f, 0.85f),
        new Data(0.024f, 0.361f, 0.522f, 0.85f),
        new Data(0f, 0.329f, 0.478f, 0.9f)
    );

    public static void init(){
        IntSeq saved = Core.settings.getJson("fc-main-pal", IntSeq.class, Integer.class, IntSeq::new);
        for(int i = 0; i < saved.size; i++){
            if(i >= dataMap.size)
                dataMap.add(new Data(1f, 1f, 1f, 1f));

            dataMap.get(i).color.set(
                saved.get(i)
            );
        }

        saveNames.addAll(
            Core.settings.getJson("fc-saveNames", Seq.class, String.class, Seq<String>::new)
        );

        ui.hudGroup.fill(t -> {
            t.name = "fc-editor-button";
            t.visibility = () -> Core.settings.getBool("fc-editor") && applied;
            (mobile ? t.top().marginTop(120f * Scl.scl()) : t.bottom()).left().button("@fc-editor-button", Icon.fill, () -> {
                rebuild();
                colorEditor.show();
            }).size(uiSize, buttonHeight);
        });

        Events.on(ResizeEvent.class, e -> {
            if(colorEditor.isShown())
                rebuild();
        });

        colorEditor.keyDown(key -> {
            if(key == KeyCode.escape || key == KeyCode.back){
                if(selected == null)
                    Core.app.post(colorEditor::hide);
                else{
                    selected = null;
                    rebuild();
                }
            }
        });
        saves.closeOnBack();

        Events.run(Trigger.update, () -> {
            if(applied){
                draw = Core.settings.getBool("fc-draw");
                return;
            }

            draw = false;
        });

        Events.on(EventType.PlayerChatEvent.class, e -> {
            if(e.player == null || e.player == player || e.message.isEmpty()) return;
            String codes = e.message;

            int pos = codes.lastIndexOf('■');
            if(pos > 0){
                codes = codes.substring(
                    codes.substring(
                        0, codes.indexOf('■')
                    ).lastIndexOf('['),
                    pos
                ).replaceAll("[]\\[]", "");
            }

            String[] buffer = codes.split("[:■]");
            if(buffer.length < dataMap.size) return;

            int[] colors = new int[buffer.length];
            for(int i = 0; i < buffer.length; i++){
                try{
                    colors[i] = Color.valueOf(buffer[i]).rgba();
                }catch(Exception ignored){
                    ui.showInfoFade(
                        Core.bundle.format(
                            "fc-shared-fail",
                            e.player.coloredName()
                        )
                    );

                    return;
                }
            }

            ui.showInfoFade(
                Core.bundle.format(
                    "fc-shared",
                    e.player.coloredName()
                )
            );

            for(int i = 0; i < shared.length; i++){
                if(shared[i] != null && shared[i].from.equals(e.player.coloredName())){
                    shared[i] = new PalCache(e.player, colors);
                    return;
                }
            }

            shared[++lastPosition % shared.length] = new PalCache(e.player, colors);
        });
    }

    public static void reloadClear(){
        draw = Core.settings.getBool("fc-draw");

        reload();
    }

    public static void reload(){
        while(dataMap.size < floodBlocks.length)
            dataMap.add(new Data(1f, 1f, 1f, 1f));

        if(draw){
            wasDrawing = true;

            syncAllRegions();
            recacheChunks();

            for(int i = 0; i < floodBlocks.length; i++)
                floodBlocks[i].destroyEffect = dataMap.get(i).effect;
        }else{
            if(wasDrawing){
                for(int i = 0; i < floodBlocks.length; i++){
                    Block b = floodBlocks[i];

                    b.region = floodTex[i];
                    b.destroyEffect = Fx.none;
                }

                recacheChunks();

                wasDrawing = false;
            }
        }
    }

    public static void recacheChunks(){
        int sx = world.width() / BlockRenderer.chunkSize, sy = world.height() / BlockRenderer.chunkSize;
        for(int x = 0; x < sx; x++)
            for(int y = 0; y < sy; y++)
                renderer.blocks.cacheChunk(floodBlocks[0].buildingCacheLayer.ordinal(), x, y);
    }

    // cache for chat-shared palettes
    final static PalCache[] shared = new PalCache[Core.settings.getInt("fc-array", 5)];
    static int lastPosition = -1;
    // the main "window" for the editor
    final static BaseDialog colorEditor = new BaseDialog("@fc-editor"){
        @Override
        public void hide(){
            super.hide();

            recacheChunks();
        }
    };
    // secondary "window" for save profiles
    final static BaseDialog saves = new BaseDialog("@fc-saves");

    // variable field table, separated from main for easier updates
    final static Table fields = new Table(), hex = new Table(), text = new Table();
    // a reusable instace of the stringbuilder
    final static StringBuilder uiBuilder = new StringBuilder();
    // instance for the preview color
    final static Color newColor = new Color();
    // selected block reference
    static Block selected;
    static int selectedIndex;
    // random string cache
    static String save;
    
    // save cache
    final static Seq<String> saveNames = new Seq<>();
    final static IntSeq saveData = new IntSeq();

    // ui scale
    final static float mult = 1 / Scl.scl(),
        sliderSize = 500f,
        fieldSize = 400f,
        spacerSize = sliderSize + 20f,
        window = sliderSize + 100f,
        uiSize = 180f,
        buttonHeight = 60f,
        uiHeight = 40f,
        lowHeight = uiHeight / 2f;

    final static Image preview = new Image(
        new Texture(
            new Pixmap(256, 256){{
                fill(Color.white);
            }}
        )
    ){{
        setScale(mult);
    }};

    public static void rebuild(){
        colorEditor.reset();

        int columns = Core.graphics.getWidth() / Scl.scl(160) > 5 ? 5 : 2;
        float width = Core.graphics.getWidth() / Scl.scl(220) > 4 ? 220f : 130f;

        if(selected == null){
            colorEditor.fill(t -> {
                for(int i = 0; i < floodBlocks.length; i++){
                    if(i % columns == 0) t.row();

                    int arr = i;
                    t.button(new TextureRegionDrawable(floodBlocks[i].region), () -> {
                        selected = floodBlocks[arr];
                        selectedIndex = arr;
                        newColor.set(dataMap.get(arr).color);

                        rebuild();
                    }).size(160f, 60f);
                }
            });

            colorEditor.fill(t -> {
                t.top().left();

                t.button("\uE81B " + Core.bundle.get("save"), () -> {
                    text.reset();
                    save = "palette";

                    text.add(Core.bundle.format("fc-save-name", save)).row();
                    if(saveNames.contains(save))
                        text.add("@fc-overwrite").color(Color.scarlet).row();

                    saves.reset();
                    saves.fill(nt -> {
                        nt.bottom().marginBottom(buttonHeight);

                        nt.button(Icon.left, saves::hide).width(uiSize);
                        nt.button(Icon.ok, () -> {
                            saveNames.addUnique(save);
                            Core.settings.putJson("fc-saveNames", String.class, saveNames);

                            saveData.clear();
                            for(int i = 0; i < dataMap.size; i++)
                                saveData.add(dataMap.get(i).color.rgba());
                            Core.settings.putJson("fc-palette-" + save, Integer.class, saveData);

                            ui.showInfoFade(Core.bundle.format("fc-saved", save));
                            saves.hide();
                        }).width(uiSize);
                    });
                    saves.fill(tb -> {
                        tb.add("@fc-enter-name").row();
                        tb.field("", in -> {
                            String name = in.replaceAll("[^a-zA-Z0-9]", "");
                            save = name.isEmpty() ? "palette" : name;

                            text.reset();
                            text.add(Core.bundle.format("fc-save-name", save)).row();
                            if(saveNames.contains(save))
                                text.add("@fc-overwrite").color(Color.scarlet).row();
                        }).width(380f).row();
                        tb.add(text).row();
                    });

                    saves.show();
                }).width(uiSize).row();
                t.button("\uE852 " + Core.bundle.get("load"), () -> {
                    saves.reset();

                    saves.fill(nt -> nt.top().marginTop(40f).add("@fc-select").width(220f));
                    saves.fill(nt -> nt.bottom().marginBottom(buttonHeight).button(Icon.left, saves::hide).width(uiSize));
                    saves.fill(tb -> {
                        if(saveNames.isEmpty()){
                            tb.add("@fc-no-saves").width(spacerSize).row();
                        }else{
                            Table worker = new Table();
                            for(int i = 0; i < saveNames.size; i++){
                                String string = saveNames.get(i);
                                if(string.isEmpty()) continue;

                                worker.button(string, () -> {
                                    saveData.clear();
                                    saveData.addAll(
                                        Core.settings.getJson("fc-palette-" + string, IntSeq.class, Integer.class, IntSeq::new)
                                    );
                                    Core.settings.putJson("fc-main-pal", Integer.class, saveData);

                                    for(int s = 0; s < saveData.size; s++){
                                        if(s >= dataMap.size)
                                            dataMap.add(new Data(1f, 1f, 1f, 1f));
                                        dataMap.get(s).color.set(saveData.get(s));
                                    }

                                    syncAllRegions();
                                    rebuild();

                                    ui.showInfoFade(Core.bundle.format("fc-loaded", string));
                                    saves.hide();
                                }).width(buttonHeight + (10f * string.length()));

                                if(i != 0 && i % columns == 0){
                                    tb.add(worker).row();
                                    worker = new Table();
                                }
                            }

                            tb.add(worker).row();
                        }
                    });

                    saves.show();
                }).width(uiSize).row();
                t.button("\uE815 " + Core.bundle.get("fc-remove"), () -> {
                    saves.reset();

                    saves.fill(nt -> nt.top().marginTop(40f).add("@fc-select").width(220f));
                    saves.fill(nt -> nt.bottom().marginBottom(buttonHeight).button(Icon.left, saves::hide).width(uiSize));
                    saves.fill(tb -> {
                        if(saveNames.isEmpty())
                            tb.add("@fc-no-saves").width(220f).row();
                        else{
                            Table worker = new Table();
                            for(int i = 0; i < saveNames.size; i++){
                                String string = saveNames.get(i);
                                if(string.isEmpty()) continue;

                                worker.button(string, () -> {
                                    saveNames.remove(string);
                                    Core.settings.putJson("fc-saveNames", String.class, saveNames);

                                    Core.settings.remove("fc-palette-" + string);

                                    ui.showInfoFade(Core.bundle.format("fc-removed", string));
                                    saves.hide();
                                }).width(buttonHeight + (10f * string.length()));

                                if(i != 0 && i % columns == 0){
                                    tb.add(worker).row();
                                    worker = new Table();
                                }
                            }

                            tb.add(worker).row();
                        }
                    });

                    saves.show();
                }).width(uiSize).row();
                t.button("\uE801 " + Core.bundle.get("fc-cache"), () -> {
                    saves.reset();

                    saves.fill(nt -> nt.top().marginTop(40f).add("@fc-select").width(220f));
                    saves.fill(nt -> nt.bottom().marginBottom(buttonHeight).button(Icon.left, saves::hide).width(uiSize));
                    saves.fill(tb -> {
                        if(Structs.count(shared, Objects::nonNull) <= 0)
                            tb.add("@fc-no-saves").width(220f).row();
                        else{
                            Table worker = new Table();
                            for(int i = 0; i < shared.length; i++){
                                if(shared[i] == null) continue;

                                PalCache cache = shared[i];
                                worker.button(cache.from, () ->
                                    ui.showConfirm(Core.bundle.format("fc-shared-confirm", cache.from), () -> {
                                        for(int in = 0; in < cache.colors.length; in++)
                                            dataMap.get(in).color.set(cache.colors[in]);

                                        writeDataMap();
                                        syncAllRegions();

                                        ui.showInfoFade("@fc-import-success");
                                    })
                                ).width(buttonHeight + (10f * Strings.stripColors(cache.from).length()));

                                if(i != 0 && i % columns == 0){
                                    tb.add(worker).row();
                                    worker = new Table();
                                }
                            }

                            tb.add(worker).row();
                        }
                    });

                    saves.show();
                }).width(uiSize).row();
            });

            colorEditor.fill(t -> {
                t.bottom();

                t.button(Icon.left, colorEditor::hide).size(width, buttonHeight);
                t.button(Icon.download, () -> {
                    String codes = Core.app.getClipboardText();
                    if(codes == null || codes.isEmpty()){
                        ui.showErrorMessage("@fc-import-fail");
                        return;
                    }

                    int pos = codes.lastIndexOf('■');
                    if(pos > 0)
                        codes = codes.substring(0, pos).replaceAll("[]\\[]", "");

                    String[] buffer = codes.split("[:■]");
                    if(buffer.length < dataMap.size){
                        ui.showErrorMessage("@fc-import-fail");
                        return;
                    }

                    ui.showConfirm("@fc-import-confirm", () -> {
                        for(int i = 0; i < dataMap.size; i++)
                            dataMap.get(i).color.set(Color.valueOf(buffer[i]));

                        writeDataMap();
                        syncAllRegions();
                        rebuild();

                        ui.showInfoFade("@fc-import-success");
                    });
                }).size(width, buttonHeight);
                t.button(Icon.copy, () -> {
                    for(int i = 0; i < dataMap.size; i++)
                        uiBuilder.append("[#").append(dataMap.get(i).color.toString()).append("]■");
                    Core.app.setClipboardText(uiBuilder.toString());

                    uiBuilder.setLength(0);
                    ui.showInfoFade("@fc-export-success");
                }).size(width, buttonHeight);
                t.button(Icon.trash, () ->
                    ui.showConfirm("@fc-confirm-all", () -> {
                        Core.settings.remove("fc-main-pal");
                        for(int i = 0; i < dataMap.size; i++){
                            Data vars = dataMap.get(i);
                            vars.reset();
                        }

                        syncAllRegions();
                        rebuild();
                    })
                ).size(width, buttonHeight);
                t.button(Icon.rotate, () ->
                    ui.showConfirm("@fc-confirm-rng", () -> {
                        Color first = randomColor(), last = randomColor();
                        if(first.a > last.a)
                            first.a = last.a;

                        for(int i = 0; i < dataMap.size; i++){
                            Color out = i == 0 ? first : i == dataMap.size - 1 ? last : null;

                            if(out == null){
                                out = new Color(first);
                                out.lerp(last, (float) i / dataMap.size);
                            }

                            dataMap.get(i).color.set(out).rgba();
                        }

                        writeDataMap();
                        syncAllRegions();

                        rebuild();
                    })
                ).size(width, buttonHeight);
            });

            return;
        }

        updateFields();
        updatePreview();

        colorEditor.pane(t -> {
            t.add(preview).row();
            t.spacer(() -> spacerSize, () -> lowHeight).row();
            t.add(hex).row();
            t.spacer(() -> spacerSize, () -> uiHeight).row();
            t.add(
                Core.bundle.get(
                    Core.settings.getBool("fc-sliders") ? "fc-color-sliders" : "fc-color-fields"
                )
            ).row();
            t.spacer(() -> spacerSize, () -> lowHeight).row();
            t.add(fields);
        }).width(window).marginBottom(buttonHeight + lowHeight);

        colorEditor.fill(t -> {
            t.bottom();

            t.button(Icon.left, () -> {
                selected = null;
                rebuild();
            }).size(width, buttonHeight);
            t.button(Icon.ok, () -> {
                int rgba = dataMap.get(selectedIndex).color.rgba();
                dataMap.get(selectedIndex).color.set(newColor);
                if(rgba != newColor.rgba()){
                    writeDataMap();
                    syncRegion(selectedIndex);
                }

                selected = null;
                rebuild();
            }).size(width, buttonHeight);
            t.button(Icon.trash, () ->
                ui.showConfirm("@fc-confirm", () -> {
                    Data vars = dataMap.get(selectedIndex);
                    vars.reset();

                    writeDataMap();
                    syncRegion(selectedIndex);
                    
                    newColor.set(vars.color);
                    rebuild();
                })
            ).size(width, buttonHeight);
        });
    }

    static void updateFields(){
        fields.reset();

        boolean sliders = Core.settings.getBool("fc-sliders");

        fields.add(Core.bundle.get("fc-color-red")).row();
        if(sliders){
            fields.slider(0f, 1f, 0.001f, newColor.r, f -> {
                newColor.r = f;

                updatePreview();
            }).size(sliderSize, uiHeight).row();
        }else{
            fields.field(newColor.r + "", TextField.TextFieldFilter.floatsOnly, f -> {
                float fl = Strings.parseFloat(f, 0);
                newColor.r = Mathf.clamp(fl);

                updatePreview();
                if(newColor.r != fl)
                    updateFields();
            }).size(fieldSize, uiHeight).row();
        }

        fields.add(Core.bundle.get("fc-color-green")).row();
        if(sliders){
            fields.slider(0f, 1f, 0.001f, newColor.g, f -> {
                newColor.g = f;

                updatePreview();
            }).size(sliderSize, uiHeight).row();
        }else{
            fields.field(newColor.g + "", TextField.TextFieldFilter.floatsOnly, f -> {
                float fl = Strings.parseFloat(f, 0);
                newColor.g = Mathf.clamp(fl);

                updatePreview();
                if(newColor.g != fl)
                    updateFields();
            }).size(fieldSize, uiHeight).row();
        }

        fields.add(Core.bundle.get("fc-color-blue")).row();
        if(sliders){
            fields.slider(0f, 1f, 0.001f, newColor.b, f -> {
                newColor.b = f;

                updatePreview();
            }).size(sliderSize, uiHeight).row();
        }else{
            fields.field(newColor.b + "", TextField.TextFieldFilter.floatsOnly, f -> {
                float fl = Strings.parseFloat(f, 0);
                newColor.b = Mathf.clamp(fl);

                updatePreview();
                if(newColor.b != fl)
                    updateFields();
            }).size(fieldSize, uiHeight).row();
        }

        fields.add(Core.bundle.get("fc-color-alpha")).row();
        if(sliders){
            fields.slider(0f, 1f, 0.001f, newColor.a, f -> {
                newColor.a = f;

                updatePreview();
            }).size(sliderSize, uiHeight).row();
        }else{
            fields.field(newColor.a + "", TextField.TextFieldFilter.floatsOnly, f -> {
                float fl = Strings.parseFloat(f, 0);
                newColor.a = Mathf.clamp(fl);

                updatePreview();
                if(newColor.a != fl)
                    updateFields();
            }).size(fieldSize, uiHeight).row();
        }
    }

    private static IntSeq n = new IntSeq();
    public static void writeDataMap(){
        n.clear();
        dataMap.each(d -> n.add(d.color.rgba()));
        Core.settings.putJson("fc-main-pal", Integer.class, n);
    }

    static void updatePreview(){
        preview.setColor(newColor);

        hex.reset();
        hex.field("#" + newColor, h -> {
            try{
                newColor.set(Color.valueOf(h));
                preview.setColor(newColor);

                updateFields();
            }catch(Throwable ignored){}
        }).size(spacerSize, uiHeight);
    }

    static void syncAllRegions(){
        for(int i = 0; i < floodBlocks.length; i++)
            syncRegion(i);
    }

    static void syncRegion(int index){
        floodBlocks[index].region = new TextureRegion(
            new Texture(
                new Pixmap(32, 32){{
                    fill(dataMap.get(index).color);
                }}
            )
        );
    }

    public static Color randomColor(){
        return new Color(
            Mathf.random(1f),
            Mathf.random(1f),
            Mathf.random(1f),
            Mathf.random(0.2f, 1f)
        );
    }
}
