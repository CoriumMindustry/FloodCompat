package floodcompat;

import arc.graphics.g2d.*;
import arc.scene.style.*;
import arc.struct.*;
import mindustry.gen.*;

import static mindustry.content.Blocks.*;

public class ScriptGen{
    public static Seq<FieldStruct> fields = Seq.with(
        new FieldStruct(
            "fc-stash", vault.fullIcon, "stash,", "", "/",
            Field.it("fc-stash.type"), Field.i("fc-stash.amount")
        ).setInfinite(),
        new FieldStruct(
            "fc-loadout", Icon.down, "loadout,", "", "/",
            Field.bl("fc-loadout.type"), Field.tt("fc-loadout.team"), Field.io("fc-loadout.rotation")
        ).setInfinite(),
        new FieldStruct(
            "fc-list", Icon.list, "list,", "", "",
            Field.bl("fc-list")
        ).setInfinite(),
        new FieldStruct(
            "fc-create.launcher", thoriumReactor.fullIcon, "create,launcher=", "", "/",
            Field.fo("fc-launcher.health"), Field.f("fc-launcher.chance"), Field.f("fc-launcher.usage"),
            Field.f("fc-launcher.range"), Field.f("fc-launcher.amount"), Field.i("fc-launcher.radius"),
            Field.i("fc-launcher.threat"), Field.fo("fc-launcher.spore")
        )
    );


    public static class FieldStruct{
        public final String name;
        public final TextureRegionDrawable icon;
        public final String init, initSeparator, separator;
        public final Field[] fields;
        public boolean infinite;

        public FieldStruct(String name, TextureRegion icon, String init, String initSeparator, String separator, Field... fields){
            this.name = name;
            this.icon = new TextureRegionDrawable(icon);
            this.init = init;
            this.initSeparator = initSeparator;
            this.separator = separator;
            this.fields = fields;
        }

        public FieldStruct(String name, TextureRegionDrawable icon, String init, String initSeparator, String separator, Field... fields){
            this.name = name;
            this.icon = icon;
            this.init = init;
            this.initSeparator = initSeparator;
            this.separator = separator;
            this.fields = fields;
        }

        /// Makes this FieldStruct create more fields as the previous ones get filled
        public FieldStruct setInfinite(){
            infinite = true;
            return this;
        }
    }

    public static class Field{
        public final String name;
        public final FieldType type;

        private Field(String name, FieldType type){
            this.name = "field." + name;
            this.type = type;
        }

        public static Field f(String name){
            return new Field(name, FieldType.Float);
        }

        public static Field i(String name){
            return new Field(name, FieldType.Int);
        }

        public static Field b(String name){
            return new Field(name, FieldType.Bool);
        }

        public static Field fo(String name){
            return new Field(name, FieldType.FloatOpt);
        }

        public static Field io(String name){
            return new Field(name, FieldType.IntOpt);
        }

        public static Field bo(String name){
            return new Field(name, FieldType.BoolOpt);
        }

        public static Field bl(String name){
            return new Field(name, FieldType.BlockType);
        }

        public static Field it(String name){
            return new Field(name, FieldType.ItemType);
        }

        public static Field tt(String name){
            return new Field(name, FieldType.TeamType);
        }
    }

    public enum FieldType{
        // variables
        Float, Int, Bool,
        // variables (optional)
        FloatOpt, IntOpt, BoolOpt,
        // types
        BlockType, ItemType, TeamType
    }
}
