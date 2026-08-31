package floodcompat;

import arc.*;
import arc.graphics.g2d.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;

import java.nio.*;

import static mindustry.Vars.*;

/** A class containing static cached setting values */
public class SettingCache{
    public static TextureRegion[] floodTex;
    public static Block[] floodBlocks;

    public static boolean applied, draw, wasDrawing;
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
                EditDrawers.reload();
            });
            t.checkPref("fc-editor", false);
            t.sliderPref("fc-array", 5, 1, 10, i -> i + "");
            t.checkPref("fc-sliders", true);

            SoundUtils.addSettings(t);
        });
    }

    public static void load(byte[] data){
        if(data.length <= 0) return;

        ByteBuffer buffer = ByteBuffer.wrap(data);

        floodTeam = Team.get(buffer.get());

        if(data.length < 2) return;
        int blocks = (data.length - 1) / 2;

        floodBlocks = new Block[blocks];
        floodTex = new TextureRegion[blocks];

        for(int i = 0; i < blocks; i++){
            Block b = content.blocks().get(buffer.getShort());

            floodBlocks[i] = b;
            floodTex[i] = b.region;
        }

        EditDrawers.reloadClear();
    }
}
