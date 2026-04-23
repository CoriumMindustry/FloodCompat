package floodcompat;

import arc.*;
import mindustry.gen.*;

import java.nio.*;

import static mindustry.Vars.*;

/** A class containing static cached setting values */
public class SettingCache{
    public static boolean applied, draw, noEffects;
    public static short floodTeam;

    public static void init(){
        ui.settings.addCategory("@fc-category", Icon.waves, t -> {
            t.sliderPref("fc-quality", 0, 0, 2, i -> Core.bundle.get("fc-quality" + i));
            t.sliderPref("fc-culling", -1, -1, 120, i -> switch(i){
                case -1 -> Core.bundle.get("fc-culling.disabled");
                case 0 -> Core.bundle.get("fc-culling.no-effects");
                default -> Core.bundle.format("fc-culling", i);
            });
            t.checkPref("fc-draw", true);
            t.checkPref("fc-editor", false);
            t.sliderPref("fc-array", 5, 1, 10, i -> i + "");
            t.checkPref("fc-sliders", true);

            SoundUtils.addSettings(t);
        });
    }

    public static void load(byte[] data){
        if(data.length <= 0) return;

        ByteBuffer buffer = ByteBuffer.wrap(data);

        floodTeam = (short) (buffer.get() & 0xff);
    }
}
