package floodcompat;

import arc.*;
import arc.audio.*;
import arc.files.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.input.*;
import arc.math.*;
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
import mindustry.mod.*;
import mindustry.type.*;
import mindustry.ui.dialogs.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.*;

import static arc.math.Angles.*;
import static arc.graphics.g2d.Draw.*;
import static mindustry.Vars.*;
import static mindustry.content.Blocks.*;

import static floodcompat.SettingCache.*;

public class EditDrawers{
    public static Mods.LoadedMod fc;
    public static Sound cachedSound;
    public static int cachedID;

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
        final int hash;

        public PalCache(Player from, int[] colors){
            this.from = from.coloredName();
            this.colors = colors;
            hash = from.uuid().hashCode();
        }
    }

    public static final OrderedMap<Block, Data> dataMap = OrderedMap.of(
        scrapWall, new Data(0.518f, 0.725f, 0.82f, 0.69f),
        titaniumWall, new Data(0.388f, 0.682f, 0.82f, 0.75f),
        thoriumWall, new Data(0.286f, 0.616f, 0.769f, 0.75f),
        phaseWall, new Data(0.208f, 0.573f, 0.741f, 0.75f),
        surgeWall, new Data(0.153f, 0.525f, 0.702f, 0.8f),
        reinforcedSurgeWall, new Data(0.106f, 0.478f, 0.651f, 0.8f),
        plastaniumWall, new Data(0.059f, 0.435f, 0.612f, 0.8f),
        berylliumWall, new Data(0.035f, 0.4f, 0.569f, 0.85f),
        tungstenWall, new Data(0.024f, 0.361f, 0.522f, 0.85f),
        carbideWall, new Data(0f, 0.329f, 0.478f, 0.9f)
    );

    public static void init(){
        cachedSound = Sounds.wind3;
        cachedID = Sounds.getSoundId(cachedSound);

        if(Core.settings.getBool("fc-customs"))
            reloadWind3();

        Seq<Block> blocks = dataMap.keys().toSeq();
        for(int i = 0; i < blocks.size; i++){
            String setting = "fc-col-" + blocks.get(i).name;
            if(!Core.settings.has(setting)) continue;

            dataMap.get(blocks.get(i)).color.set(
                Core.settings.getInt(setting)
            );
        }

        saveNames.addAll(
            Core.settings.getJson("fc-saveNames", Seq.class, String.class, Seq::new)
        );

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
            t.checkPref("fc-customs", false, b -> reloadWind3());

            t.row().button("@fc-choose", () ->
                platform.showMultiFileChooser(file -> {
                    try{
                        Sound sound = new Sound(file);
                        sound.play();

                        Core.settings.put("fc-wind3-path", file.path());
                        if(Core.settings.getBool("fc-customs", false))
                            reloadWind3();
                    }catch(Exception ex){
                        ui.showErrorMessage("@fc-file-error");
                        Core.settings.put("fc-wind3-path", "null");
                        reloadWind3();
                    }
                }, "mp3", "ogg")
            ).width(240f);
        });

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

        Events.run(Trigger.update, () -> {
            if(applied){
                draw = (
                    (Core.settings.getBool("fc-draw")
                        || Core.settings.getInt("fc-quality") == 2)
                );
                noEffects = Core.settings.getInt("fc-quality") > 0;

                return;
            }

            draw = false;
        });

        Events.on(EventType.PlayerChatEvent.class, e -> {
            if(e.player == null || e.player == player || e.message.isEmpty()) return;
            String codes = e.message;

            int pos = codes.lastIndexOf('■');
            if(pos > 0)
                codes = codes.substring(0, pos).replaceAll("[]\\[]", "");

            String[] buffer = codes.split("[:■]");
            if(buffer.length < dataMap.size) return;

            int[] colors = new int[buffer.length];
            for(int i = 0; i < buffer.length; i++)
                colors[i] = Color.valueOf(buffer[i]).rgba();

            ui.showInfoFade(
                Core.bundle.format(
                    "fc-shared",
                    e.player.coloredName()
                )
            );

            for(int i = 0; i < shared.length; i++){
                if(shared[i] != null && shared[i].hash == e.player.uuid().hashCode()){
                    shared[i] = new PalCache(e.player, colors);
                    return;
                }
            }

            shared[++lastPosition % shared.length] = new PalCache(e.player, colors);
        });

        Vars.content.blocks().each(b -> {
            if(b instanceof Wall w && dataMap.containsKey(w)){
                w.buildType = () -> w.new WallBuild(){
                    final Data flood = dataMap.get(w);

                    public boolean isFlood(){
                        return(
                            draw
                            && team.id == floodTeam
                        );
                    }

                    @Override
                    public void draw(){
                        if(isFlood()){
                            Draw.color(flood.color);
                            Fill.rect(x, y, w.region.width * w.region.scl() * xscl, w.region.height * w.region.scl() * xscl);
                            Draw.reset();

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
                    public boolean collision(Bullet bullet){
                        boolean wasDead = health <= 0;

                        float damage = bullet.type.buildingDamage(bullet);
                        if(!bullet.type.pierceArmor)
                            damage = Damage.applyArmor(damage, w.armor);

                        damage(bullet, bullet.team, damage);
                        if(health <= 0 && !wasDead)
                            Events.fire(new BuildingBulletDestroyEvent(this, bullet));

                        hit = 1f;

                        // flood does not have such stats
                        if(!applied || team.id != floodTeam){
                            if(w.lightningChance > 0f){
                                if(Mathf.chance(w.lightningChance)){
                                    Lightning.create(team, w.lightningColor, w.lightningDamage, x, y, bullet.rotation() + 180f, w.lightningLength);
                                    w.lightningSound.at(tile, Mathf.random(0.9f, 1.1f));
                                }
                            }

                            if(w.chanceDeflect > 0f){
                                if(bullet.vel.len() <= 0.1f || !bullet.type.reflectable) return true;

                                if(!Mathf.chance(w.chanceDeflect / bullet.damage())) return true;

                                w.deflectSound.at(tile, Mathf.random(0.9f, 1.1f));

                                bullet.trns(-bullet.vel.x, -bullet.vel.y);

                                float penX = Math.abs(x - bullet.x), penY = Math.abs(y - bullet.y);

                                if(penX > penY){
                                    bullet.vel.x *= -1;
                                }else{
                                    bullet.vel.y *= -1;
                                }

                                bullet.owner = this;
                                bullet.team = team;
                                bullet.time += 1f;

                                return false;
                            }
                        }

                        return true;
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
                        Damage.dynamicExplosion(
                            x, y, flammability * block.flammabilityScale, explosiveness * 3.5f * block.explosivenessScale, power, tilesize * block.size / 2f, state.rules.damageExplosions, !isFlood(), null,
                            noEffects ? Fx.none : isFlood() ? flood.effect : block.destroyEffect,
                            isFlood() ? 0f : block.baseShake
                        );

                        if(!isFlood() && !noEffects && block.createRubble && !floor().solid && !floor().isLiquid)
                            Effect.rubble(x, y, block.size);
                    }
                };
            }
        });
    }

    public static void reloadWind3(){
        ObjectIntMap<Sound> soundToId = Reflect.get(Sounds.class, ObjectIntMap.class, "soundToId");
        soundToId.remove(Sounds.wind3);

        if(Core.settings.getBool("fc-wind3", false)){
            if(Core.settings.getString("fc-wind3-path", "null").equals("null")){
                ZipFi jar = new ZipFi(fc.file);
                Sounds.wind3 = new Sound(
                    jar.child("sounds").child("cr.ogg")
                );
            }else try{
                Sounds.wind3 = new Sound(
                    Core.files.absolute(
                        Core.settings.getString("fc-wind3-path", "null")
                    )
                );
            }catch(Exception e){
                Core.settings.put("fc-wind3-path", "null");
                Sounds.wind3 = cachedSound;
            }
        }else Sounds.wind3 = cachedSound;

        IntMap<Sound> idToSound = Reflect.get(Sounds.class, IntMap.class, "idToSound");
        idToSound.put(cachedID, Sounds.wind3);
        soundToId.put(Sounds.wind3, cachedID);
    }

    // cache for chat-shared palettes
    final static PalCache[] shared = new PalCache[Core.settings.getInt("fc-array", 5)];
    static int lastPosition = -1;
    // the main "window" for the editor
    final static BaseDialog colorEditor = new BaseDialog("@fc-editor");
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
            Seq<Block> data = dataMap.keys().toSeq();
            colorEditor.fill(t -> {
                for(int i = 0; i < data.size; i++){
                    if(i % columns == 0) t.row();

                    int arr = i;
                    t.button(data.get(i).emoji(), () -> {
                        selected = data.get(arr);
                        newColor.set(dataMap.get(selected).color);

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
                            for(int i = 0; i < data.size; i++)
                                saveData.add(dataMap.get(data.get(i)).color.rgba());
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

                                    for(int s = 0; s < data.size; s++){
                                        Core.settings.put("fc-col-" + data.get(s).name, saveData.get(s));
                                        dataMap.get(data.get(s)).color.set(saveData.get(s));
                                    }

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
                        if(Structs.count(shared, s -> s != null) <= 0)
                            tb.add("@fc-no-saves").width(220f).row();
                        else{
                            Table worker = new Table();
                            for(int i = 0; i < shared.length; i++){
                                if(shared[i] == null) continue;

                                PalCache cache = shared[i];
                                worker.button(cache.from, () ->
                                    ui.showConfirm(Core.bundle.format("fc-shared-confirm", cache.from), () -> {
                                        for(int in = 0; in < cache.colors.length; in++)
                                            Core.settings.put("fc-col-" + data.get(in).name, dataMap.get(data.get(in)).color.set(cache.colors[in]).rgba());
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
                    if(buffer.length < data.size){
                        ui.showErrorMessage("@fc-import-fail");
                        return;
                    }

                    ui.showConfirm("@fc-import-confirm", () -> {
                        for(int i = 0; i < data.size; i++)
                            Core.settings.put("fc-col-" + data.get(i).name, dataMap.get(data.get(i)).color.set(Color.valueOf(buffer[i])).rgba());
                        ui.showInfoFade("@fc-import-success");
                    });
                }).size(width, buttonHeight);
                t.button(Icon.copy, () -> {
                    for(int i = 0; i < data.size; i++)
                        uiBuilder.append("[#").append(dataMap.get(data.get(i)).color.toString()).append("]■");
                    Core.app.setClipboardText(uiBuilder.toString());

                    uiBuilder.setLength(0);
                    ui.showInfoFade("@fc-export-success");
                }).size(width, buttonHeight);
                t.button(Icon.trash, () ->
                    ui.showConfirm("@fc-confirm-all", () -> {
                        for(int i = 0; i < data.size; i++){
                            Core.settings.remove("fc-col-" + data.get(i).name);
                            Data vars = dataMap.get(data.get(i));
                            vars.reset();
                        }

                        rebuild();
                    })
                ).size(width, buttonHeight);
                t.button(Icon.rotate, () ->
                    ui.showConfirm("@fc-confirm-rng", () -> {
                        Color first = randomColor(), last = randomColor();
                        if(first.a > last.a)
                            first.a = last.a;

                        for(int i = 0; i < data.size; i++){
                            Color out = i == 0 ? first : i == data.size - 1 ? last : null;

                            if(out == null){
                                out = new Color(first);
                                out.lerp(last, (float) i / data.size);
                            }

                            Core.settings.put("fc-col-" + data.get(i).name, dataMap.get(data.get(i)).color.set(out).rgba());
                        }
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
                if(dataMap.get(selected).rgba != newColor.rgba())
                    Core.settings.put("fc-col-" + selected.name, newColor.rgba());

                dataMap.get(selected).color.set(newColor);
                selected = null;

                rebuild();
            }).size(width, buttonHeight);
            t.button(Icon.trash, () ->
                ui.showConfirm("@fc-confirm", () -> {
                    Core.settings.remove("fc-col-" + selected.name);

                    Data vars = dataMap.get(selected);
                    vars.reset();
                    
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

    public static Color randomColor(){
        return new Color(
            Mathf.random(1f),
            Mathf.random(1f),
            Mathf.random(1f),
            Mathf.random(0.2f, 1f)
        );
    }
}
