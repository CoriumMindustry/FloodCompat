package floodcompat;

public class ScriptAssembler{
    private enum FieldType{
        Int, Float, Boolean, Block, Item, Option
    }
    private enum Entry{
        OptionBlock(
            "option-block", "=",
            new FieldStack(
                FieldType.Option, ".option", ".opt-desc",
                FieldType.Block, ".block", "block-desc"
            ),
            "remove", "merge", "replace"
        ),
        ItemInt(
            "item-int", "=",
            new FieldStack(
                FieldType.Item, ".item", ".item-desc",
                FieldType.Int, ".val", ".val-desc"
            )
        );

        final String name, separator;
        final FieldStack fields;
        String[] options;

        Entry(String name, String separator, FieldStack fields){
            this.name = name;
            this.separator = separator;
            this.fields = fields;
        }

        Entry(String name, String separator, FieldStack fields, String... options){
            this.name = name;
            this.separator = separator;
            this.fields = fields;
            this.options = options;
        }
    }
    private enum Header{
        Table("header.table", "table,", Entry.OptionBlock, Entry.ItemInt);

        final String name, internal;
        final Entry[] entries;

        Header(String name, String internal, Entry... entries){
            this.name = name;
            this.internal = internal;
            this.entries = entries;
        }
    }

    private static class FieldStack{
        final FieldType[] fields;
        final String[] titles;
        final String[] descriptions;

        public FieldStack(Object... values){
            int size = values.length / 3;

            fields = new FieldType[size];
            titles = new String[size];
            descriptions = new String[size];

            for(int i = 0; i < size; i++){
                fields[i] =  (FieldType) values[i];
                titles[i] = (String) values[++i];
                descriptions[i] = (String) values[++i];
            }
        }
    }
}
