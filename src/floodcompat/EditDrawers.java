package floodcompat;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.input.*;
import arc.math.*;
import arc.scene.event.*;
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
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.*;

import static arc.math.Angles.*;
import static mindustry.Vars.*;
import static arc.graphics.g2d.Draw.*;
import static mindustry.content.Blocks.*;

public class EditDrawers{
    public static class Data{
        final Effect effect;
        final Color color;
        final int rgba;

        public Data(Color color){
            this.color = color;
            this.rgba = color.rgba();

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

    public static final OrderedMap<Block, Data> dataMap = OrderedMap.of(
        scrapWall, new Data(new Color(0.518f, 0.725f, 0.82f, 0.69f)),
        titaniumWall, new Data(new Color(0.388f, 0.682f, 0.82f, 0.75f)),
        thoriumWall, new Data(new Color(0.286f, 0.616f, 0.769f, 0.75f)),
        phaseWall, new Data(new Color(0.208f, 0.573f, 0.741f, 0.75f)),
        surgeWall, new Data(new Color(0.153f, 0.525f, 0.702f, 0.8f)),
        reinforcedSurgeWall, new Data(new Color(0.106f, 0.478f, 0.651f, 0.8f)),
        plastaniumWall, new Data(new Color(0.059f, 0.435f, 0.612f, 0.8f)),
        berylliumWall, new Data(new Color(0.035f, 0.4f, 0.569f, 0.85f)),
        tungstenWall, new Data(new Color(0.024f, 0.361f, 0.522f, 0.85f)),
        carbideWall, new Data(new Color(0f, 0.329f, 0.478f, 0.9f))
    );

    public static void init(){
        Seq<Block> blocks = dataMap.keys().toSeq();
        for(int i = 0; i < blocks.size; i++){
            String setting = "fc-col-" + blocks.get(i).name;
            if(!Core.settings.has(setting)) continue;

            dataMap.get(blocks.get(i)).color.set(
                Core.settings.getInt(setting)
            );
        }

        ui.settings.addCategory("@fc-category", Icon.waves, t -> {
            t.sliderPref("fc-quality", 0, 0, 2, i -> Core.bundle.get("fc-quality" + i));
            t.checkPref("fc-draw", true);
            t.checkPref("fc-editor", false);
        });

        ui.hudGroup.fill(t -> {
            t.name = "fc-editor-button";
            t.visibility = () -> Core.settings.getBool("fc-editor");
            t.bottom().left().button("@fc-editor-button", Icon.fill, () -> {
                rebuild();
                colorEditor.show();
            }).size(180f, 60f);
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

        Vars.content.blocks().each(b -> {
            if(b instanceof Wall w && dataMap.containsKey(w)){
                w.buildType = () -> w.new WallBuild(){
                    final Data flood = dataMap.get(w);

                    public boolean isFlood(){
                        return(
                            team.id == Team.blue.id
                            && Core.settings.getBool("fc-applied")
                            && (Core.settings.getBool("fc-draw")
                            || Core.settings.getInt("fc-quality") == 2)
                        );
                    }

                    @Override
                    public void draw(){
                        if(isFlood()){
                            Draw.color(flood.color);
                            Fill.rect(x, y, w.region.width * w.region.scl() * xscl, w.region.height * w.region.scl() * xscl);

                            return;
                        }

                        super.draw();
                    }

                    @Override
                    public void drawTeam(){
                        if(isFlood()) return;

                        super.drawTeam();
                    }

                    @Override
                    public void killed(){
                        dead = true;
                        Events.fire(new EventType.BlockDestroyEvent(tile));

                        if(!isFlood())
                            block.destroySound.at(tile, Mathf.random(block.destroyPitchMin, block.destroyPitchMax));

                        onDestroyed();
                        if(tile != emptyTile)
                            tile.remove();

                        remove();
                        afterDestroyed();
                    }

                    @Override
                    public void onDestroyed(){
                        float explosiveness = block.baseExplosiveness;
                        float flammability = 0f;
                        float power = 0f;

                        if(block.hasItems){
                            for(Item item : content.items()){
                                int amount = Math.min(items.get(item), explosionItemCap());
                                explosiveness += item.explosiveness * amount;
                                flammability += item.flammability * amount;
                                power += item.charge * Mathf.pow(amount, 1.1f) * 150f;
                            }
                        }

                        if(block.hasLiquids){
                            flammability += liquids.sum((liquid, amount) -> liquid.flammability * amount / 2f);
                            explosiveness += liquids.sum((liquid, amount) -> liquid.explosiveness * amount / 2f);
                        }

                        if(block.consPower != null && block.consPower.buffered)
                            power += this.power.status * block.consPower.capacity;

                        if(block.hasLiquids && state.rules.damageExplosions)
                            liquids.each(this::splashLiquid);

                        boolean isFlood = isFlood(), noEffects = Core.settings.getInt("fc-quality") > 0;

                        //cap explosiveness so fluid tanks/vaults don't instakill units
                        Damage.dynamicExplosion(
                            x, y, flammability * block.flammabilityScale, explosiveness * 3.5f * block.explosivenessScale, power, tilesize * block.size / 2f, state.rules.damageExplosions, !isFlood, null,
                            noEffects ? Fx.none : isFlood ? flood.effect : block.destroyEffect,
                            isFlood ? 0f : block.baseShake
                        );

                        if(!isFlood && !noEffects && block.createRubble && !floor().solid && !floor().isLiquid)
                            Effect.rubble(x, y, block.size);
                    }
                };
            }
        });
    }

    // the main "window" for the editor
    static BaseDialog colorEditor = new BaseDialog("@fc-editor");
    // a cheesy way to preview the color
    static ImageButton preview = new ImageButton(Tex.whiteui, Styles.clearNonei){{
        touchable = Touchable.disabled;
        resizeImage(128f);
    }};
    // variable field tables, separated from main for easier updates
    static Table hex = new Table(), fields = new Table();
    // a reusable instace of the stringbuilder
    static StringBuilder uiBuilder = new StringBuilder();
    // instance for the preview color
    static Color newColor;
    // selected block pointer
    static Block selected;
    // ui scale
    static float mult = 1 / Scl.scl();

    public static void rebuild(){
        colorEditor.reset();

        int columns = Core.graphics.getWidth() / Scl.scl(160) > 5 ? 5 : 2;
        float width = Core.graphics.getWidth() / Scl.scl(220) > 4 ? 220f : 130f;

        if(selected == null){
            Seq<Block> data = dataMap.keys().toSeq();
            colorEditor.fill(t -> {
                for(int i = 0; i < data.size; i++){
                    if(i % columns == 0) t.row();

                    int arr = i;
                    t.button(data.get(i).emoji(), () -> {
                        selected = data.get(arr);
                        newColor = dataMap.get(selected).color.cpy();

                        rebuild();
                    }).size(160f, 60f);
                }
            });

            colorEditor.fill(t -> {
                t.bottom().button(Icon.left, () -> colorEditor.hide()).size(width, 50f);
                t.bottom().button(Icon.download, () -> {
                    String[] buffer = Core.app.getClipboardText().split(":");
                    if(buffer.length < data.size){
                        ui.showErrorMessage("@fc-import-fail");
                        return;
                    }

                    ui.showConfirm("@fc-import-confirm", () -> {
                        for(int i = 0; i < data.size; i++)
                            dataMap.get(data.get(i)).color.set(Color.valueOf(buffer[i]));
                        ui.showInfoFade("@fc-import-success");
                    });
                }).size(width, 50f);
                t.bottom().button(Icon.copy, () -> {
                    for(int i = 0; i < data.size; i++)
                        uiBuilder.append(dataMap.get(data.get(i)).color.toString()).append(":");

                    uiBuilder.setLength(uiBuilder.length() - 1);
                    Core.app.setClipboardText(uiBuilder.toString());

                    uiBuilder.setLength(0);
                    ui.showInfoFade("@fc-export-success");
                }).size(width, 50f);
                t.bottom().button(Icon.trash, () ->
                    ui.showConfirm("@fc-confirm-all", () -> {
                        for(int i = 0; i < data.size; i++){
                            Core.settings.remove("fc-col-" + data.get(i).name);
                            Data vars = dataMap.get(data.get(i));
                            vars.reset();
                        }

                        rebuild();
                    })
                ).size(width, 50f);
            });

            return;
        }

        updateHex();
        updateFields();

        colorEditor.table(t -> {
            t.button(selected.emoji(), () -> {
                selected = null;
                rebuild();
            }).row();
            t.spacer(() -> 240f, () -> 30f).row();
            preview.getStyle().imageUpColor = newColor;
            t.add(preview).row();
            t.add(hex).row();
            t.spacer(() -> 240f, () -> 40f).row();
            t.add(Core.bundle.get("fc-color-text")).row();
            t.spacer(() -> 240f, () -> 15f).row();
            t.add(fields);
        });

        colorEditor.fill(t -> {
            t.bottom().button(Icon.left, () -> {
                selected = null;
                rebuild();
            }).size(width, 50f);
            t.bottom().button(Icon.ok, () -> {
                if(dataMap.get(selected).rgba != newColor.rgba())
                    Core.settings.put("fc-col-" + selected.name, newColor.rgba());

                dataMap.get(selected).color.set(newColor);
                selected = null;

                rebuild();
            }).size(width, 50f);
            t.bottom().button(Icon.trash, () ->
                ui.showConfirm("@fc-confirm", () -> {
                    Core.settings.remove("fc-col-" + selected.name);

                    Data vars = dataMap.get(selected);
                    vars.reset();
                    
                    newColor = vars.color.cpy();
                    rebuild();
                })
            ).size(width, 50f);
        });
    }

    static void updateHex(){
        hex.reset();

        hex.field("#" + newColor.toString(), h -> {
            try{
                newColor.set(Color.valueOf(h));
                updateFields();
            }catch(Throwable ignored){}
        }).size(192f*mult, 48f*mult);
    }
    static void updateFields(){
        fields.reset();

        fields.add(Core.bundle.get("fc-color-red")).row();
        fields.field(newColor.r + "", TextField.TextFieldFilter.floatsOnly, f -> {
            float fl = Strings.parseFloat(f, 0);
            newColor.r = Mathf.clamp(fl);
            updateHex();
            if(newColor.r != fl)
                updateFields();
        }).size(400f*mult, 40f*mult).row();
        fields.add(Core.bundle.get("fc-color-green")).row();
        fields.field(newColor.g + "", TextField.TextFieldFilter.floatsOnly, f -> {
            float fl = Strings.parseFloat(f, 0);
            newColor.g = Mathf.clamp(fl);
            updateHex();
            if(newColor.g != fl)
                updateFields();
        }).size(400f*mult, 40f*mult).row();
        fields.add(Core.bundle.get("fc-color-blue")).row();
        fields.field(newColor.b + "", TextField.TextFieldFilter.floatsOnly, f -> {
            float fl = Strings.parseFloat(f, 0);
            newColor.b = Mathf.clamp(fl);
            updateHex();
            if(newColor.b != fl)
                updateFields();
        }).size(400f*mult, 40f*mult).row();
        fields.add(Core.bundle.get("fc-color-alpha")).row();
        fields.field(newColor.a + "", TextField.TextFieldFilter.floatsOnly, f -> {
            float fl = Strings.parseFloat(f, 0);
            newColor.a = Mathf.clamp(fl);
            updateHex();
            if(newColor.a != fl)
                updateFields();
        }).size(400f*mult, 40f*mult).row();
    }
}
