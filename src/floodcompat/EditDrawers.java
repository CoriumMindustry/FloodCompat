package floodcompat;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.gen.Icon;
import mindustry.type.*;
import mindustry.ui.dialogs.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.*;

import static arc.math.Angles.*;
import static mindustry.Vars.*;
import static arc.graphics.g2d.Draw.*;
import static mindustry.content.Blocks.*;

public class EditDrawers{
    static BaseDialog colorEditor = new BaseDialog("@fc-editor");
    static Table preview = new Table();
    static Color newColor;
    static Block selected;

    public static class Data{
        final Effect effect;
        final Color color;
        final int rgba;

        public Data(Color color){
            this.color = color;
            this.rgba = color.rgba8888();

            effect = new Effect(25, e -> {
                color(color);
                randLenVectors(e.id, e.fin(), Mathf.round(12 * color.a), 12f, (x, y, fin, fout) -> {
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
            int color = Core.settings.getInt("fc-col-" + blocks.get(i).name, -1);
            if(color >= 0)
                dataMap.get(blocks.get(i)).color.set(color);
        }

        ui.settings.addCategory("@fc-category", t -> {
            t.checkPref("fc-draw", true);
        });

        ui.hudGroup.fill(t -> {
            t.name = "fc-editor-button";
            t.visibility = () -> Core.input.ctrl();
            t.bottom().left().button("@fc-editor", Icon.fill, () -> {
                rebuild();
                colorEditor.show();
            }).size(240f, 80f);
        });

        Vars.content.blocks().each(b -> {
            if(b instanceof Wall w && dataMap.containsKey(w)){
                w.buildType = () -> w.new WallBuild(){
                    final Data flood = dataMap.get(w);

                    public boolean isFlood(){
                        return(
                            team == Team.blue
                            && Core.settings.getBool("fc-applied")
                            && Core.settings.getBool("fc-draw")
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

                        boolean isFlood = isFlood();

                        //cap explosiveness so fluid tanks/vaults don't instakill units
                        Damage.dynamicExplosion(x, y, flammability * block.flammabilityScale, explosiveness * 3.5f * block.explosivenessScale, power, tilesize * block.size / 2f, state.rules.damageExplosions, !isFlood, null, isFlood ? flood.effect : block.destroyEffect, block.baseShake);

                        if(!isFlood && block.createRubble && !floor().solid && !floor().isLiquid)
                            Effect.rubble(x, y, block.size);
                    }
                };
            }
        });
    }

    public static void rebuild(){
        colorEditor.reset();

        if(selected == null){
            Seq<Block> data = dataMap.keys().toSeq();
            colorEditor.fill(t -> {
                for(int i = 0; i < data.size; i++){
                    if(i % 5 == 0) t.row();

                    int arr = i;
                    t.button(data.get(i).emoji(), () -> {
                        selected = data.get(arr);

                        newColor = dataMap.get(selected).color.cpy();
                        rebuildPreview();

                        rebuild();
                    }).size(160f, 60f);
                }
            });

            colorEditor.fill(t ->
                t.bottom().button(Icon.cancel, () -> colorEditor.hide()).size(220f, 40f)
            );

            return;
        }

        colorEditor.table(t -> {
            t.button(selected.emoji(), () -> {
                selected = null;
                rebuild();
            }).row();
            t.spacer(() -> 240f, () -> 40f).row();
            t.add(preview).row();
            t.spacer(() -> 240f, () -> 40f).row();
            t.add(Core.bundle.get("fc-color-text")).row();
            t.spacer(() -> 240f, () -> 60f).row();
            t.add(Core.bundle.get("fc-color-red")).row();
            t.field(newColor.r + "", TextField.TextFieldFilter.floatsOnly, f -> {
                float fl = Strings.parseFloat(f, 0);
                newColor.r = Mathf.clamp(fl);
                rebuildPreview();
                if(newColor.r != fl)
                    rebuild();
            }).size(400f, 60f).row();
            t.add(Core.bundle.get("fc-color-green")).row();
            t.field(newColor.g + "", TextField.TextFieldFilter.floatsOnly, f -> {
                float fl = Strings.parseFloat(f, 0);
                newColor.g = Mathf.clamp(fl);
                rebuildPreview();
                if(newColor.g != fl)
                    rebuild();
            }).size(400f, 60f).row();
            t.add(Core.bundle.get("fc-color-blue")).row();
            t.field(newColor.b + "", TextField.TextFieldFilter.floatsOnly, f -> {
                float fl = Strings.parseFloat(f, 0);
                newColor.b = Mathf.clamp(fl);
                rebuildPreview();
                if(newColor.b != fl)
                    rebuild();
            }).size(400f, 60f).row();
            t.add(Core.bundle.get("fc-color-alpha")).row();
            t.field(newColor.a + "", TextField.TextFieldFilter.floatsOnly, f -> {
                float fl = Strings.parseFloat(f, 0);
                newColor.a = Mathf.clamp(fl);
                rebuildPreview();
                if(newColor.a != fl)
                    rebuild();
            }).size(400f, 60f).row();
        });

        colorEditor.fill(t ->{
            t.bottom().button(Icon.ok, () -> {
                Core.settings.put("fc-col-" + selected.name, newColor.rgba8888());
                dataMap.get(selected).color.set(newColor);

                selected = null;
                rebuild();
            }).size(220f, 40f);
            t.bottom().button(Icon.cancel, () -> {
                selected = null;
                rebuild();
            }).size(220f, 40f);
            t.bottom().button(Icon.download, () ->
                ui.showConfirm("@fc-confirm", () -> {
                    Data vars = dataMap.get(selected);

                    vars.color.set(vars.rgba);
                    rebuild();

                    newColor = dataMap.get(selected).color.cpy();
                    rebuildPreview();
                })
            ).size(220f, 40f);
        });
    }

    public static void rebuildPreview(){
        preview.reset();

        preview.fill(t ->
            t.image(Icon.file, newColor)
        );
    }
}
