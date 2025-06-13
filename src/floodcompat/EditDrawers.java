package floodcompat;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.struct.*;
import mindustry.*;
import mindustry.game.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.*;

import static arc.graphics.g2d.Draw.*;
import static mindustry.content.Blocks.*;

public class EditDrawers{
    public static ObjectMap<Block, Color> colors = ObjectMap.of(
        scrapWall, new Color(0.482f, 0.655f, 0.804f, 0.33f),
        titaniumWall, new Color(0.384f, 0.541f, 0.761f, 0.47f),
        thoriumWall, new Color(0.306f, 0.451f, 0.765f, 0.52f),
        phaseWall, new Color(0.161f, 0.353f, 0.769f, 0.7f),
        surgeWall, new Color(0.145f, 0.216f, 0.784f, 0.8f),
        reinforcedSurgeWall, new Color(0.118f, 0.118f, 0.663f, 0.89f),
        plastaniumWall, new Color(0.063f, 0.063f, 0.58f, 0.91f),
        berylliumWall, new Color(0.039f, 0.039f, 0.463f, 0.89f),
        tungstenWall, new Color(0.02f, 0.02f, 0.337f, 0.89f),
        carbideWall, new Color(0.016f, 0.016f, 0.196f)
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
                };
            }
        });
    }
}
