package floodcompat;

import arc.*;
import arc.graphics.g2d.*;
import arc.struct.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.*;

import java.nio.*;

import static mindustry.Vars.*;

/** A class containing static cached setting values */
public class SettingCache{
    public static TextureRegion[] floodTex;
    public static Effect[] floodFx;
    public static Block[] floodBlocks;

    public static boolean applied, draw, wasDrawing, autotile;
    public static int fetchFreq;
    public static Team floodTeam;

    public static void init(){
        ui.settings.addCategory("@fc-category", Icon.waves, t -> {
            t.sliderPref("fc-culling", -1, -1, 120, i -> switch(i){
                case -1 -> Core.bundle.get("fc-culling.disabled");
                case 0 -> Core.bundle.get("fc-culling.no-effects");
                default -> Core.bundle.format("fc-culling", i);
            });
            t.sliderPref("fc-freq", 0, 0, 15, i -> {
                fetchFreq = i <= 0 ? 0 : 60 / i;
                return i == 0 ? Core.bundle.get("disabled") : i + "/s";
            });
            t.checkPref("fc-draw", true, b -> {
                draw = b;
                if(applied) EditDrawers.reload();
            });
            t.checkPref("fc-autotiled", false, b -> {
                autotile = b;
                if(floodBlocks != null){
                    for(int i = 0; i < floodBlocks.length; i++)
                        if(floodBlocks[i] instanceof Wall w)
                            w.autotile = b;
                }

                if(applied){
                    if(b) EditDrawers.recacheTiled();
                    else EditDrawers.reload();
                }
            });
            t.checkPref("fc-editor", false);
            t.sliderPref("fc-array", 5, 1, 10, i -> i + "");
            t.checkPref("fc-sliders", true);

            SoundUtils.addSettings(t);
        });

        fetchFreq = Core.settings.getInt("fc-freq");
        if(fetchFreq > 0) fetchFreq = 60 / fetchFreq;
        autotile = Core.settings.getBool("fc-autotiled");
    }

    public static void load(byte[] data){
        if(data.length <= 0) return;

        ByteBuffer buffer = ByteBuffer.wrap(data);

        floodTeam = Team.get(buffer.get());

        if(data.length < 3) return;
        int blocks = (data.length - 1) / 2;

        floodFx = new Effect[blocks];
        floodBlocks = new Block[blocks];
        floodTex = new TextureRegion[blocks];

        for(int i = 0; i < blocks; i++){
            Block b = content.blocks().get(buffer.getShort());

            if(b instanceof Wall w)
                w.autotile = autotile;

            floodBlocks[i] = b;
            floodFx[i] = b.destroyEffect;
        }

        EditDrawers.reloadClear();
    }
}
