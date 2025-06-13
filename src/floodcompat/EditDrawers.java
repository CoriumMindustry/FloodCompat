package floodcompat;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.*;
import mindustry.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.*;

import static mindustry.Vars.*;
import static arc.graphics.g2d.Draw.*;
import static mindustry.content.Blocks.*;

public class EditDrawers{
    public static ObjectMap<Block, Color> colors = ObjectMap.of(
        scrapWall, new Color(0.518f, 0.725f, 0.82f, 0.69f),
        titaniumWall, new Color(0.388f, 0.682f, 0.82f, 0.75f),
        thoriumWall, new Color(0.286f, 0.616f, 0.769f, 0.75f),
        phaseWall, new Color(0.208f, 0.573f, 0.741f, 0.75f),
        surgeWall, new Color(0.153f, 0.525f, 0.702f, 0.8f),
        reinforcedSurgeWall, new Color(0.106f, 0.478f, 0.651f, 0.8f),
        plastaniumWall, new Color(0.059f, 0.435f, 0.612f, 0.8f),
        berylliumWall, new Color(0.035f, 0.4f, 0.569f, 0.85f),
        tungstenWall, new Color(0.024f, 0.361f, 0.522f, 0.85f),
        carbideWall, new Color(0f, 0.329f, 0.478f, 0.9f)
    );

    public static void init(){
        Vars.content.blocks().each(b -> {
            if(b instanceof Wall w && colors.containsKey(w)){
                w.buildType = () -> w.new WallBuild(){
                    final Color color = colors.get(w);

                    public boolean isFlood(){
                        return team == Team.blue && Core.settings.getBool("fc-applied");
                    }

                    @Override
                    public void draw(){
                        if(isFlood()){
                            Draw.color(color);
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

                        //cap explosiveness so fluid tanks/vaults don't instakill units
                        Damage.dynamicExplosion(x, y, flammability * block.flammabilityScale, explosiveness * 3.5f * block.explosivenessScale, power, tilesize * block.size / 2f, state.rules.damageExplosions, block.destroyEffect, block.baseShake);

                        if(!isFlood() && block.createRubble && !floor().solid && !floor().isLiquid)
                            Effect.rubble(x, y, block.size);
                    }
                };
            }
        });
    }
}
