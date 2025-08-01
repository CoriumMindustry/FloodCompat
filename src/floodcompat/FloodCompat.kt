package floodcompat

import arc.*
import arc.func.*
import arc.math.*
import arc.math.geom.*
import arc.struct.*
import arc.util.*
import arc.util.serialization.*
import mindustry.Vars.*
import mindustry.ai.*
import mindustry.content.*
import mindustry.content.Blocks.*
import mindustry.content.UnitTypes.*
import mindustry.entities.abilities.*
import mindustry.entities.bullet.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.mod.*
import mindustry.world.*
import mindustry.world.blocks.defense.turrets.*
import java.lang.reflect.*
import java.nio.*

// Based on old foo's implementation
class FloodCompat : Mod() {
    /** Vanilla values of changed vars for restoration later */
    private val defaults = mutableListOf<Any>()
    /** All the tiles that currently have effects drawn on top */
    private val allTiles = ObjectSet<Tile>()
    private val allTasks = Seq<Timer.Task>()

    /** Used to prevent flood from applying twice */
    private var applied = false
    /** Time of the last version fetch, in millis */
    private var lastFetch = 0L
    /** Whether the mod's up to date */
    private var newest = false

    override fun init() {
        Log.info("Flood Compatibility loaded!")

        Events.on(EventType.ClientLoadEvent::class.java) {
            EditDrawers.init()
            Core.settings.put("fc-applied", false)
        }

        // call onWorldLoad() - it's necessary for /sync to not disable the mod
        Events.on(EventType.WorldLoadEvent::class.java) { onWorldLoad() }

        Events.on(EventType.ResetEvent::class.java) { disable() }
        if (!state.isMenu) onWorldLoad() // Mod was initialized after loading a world (realistically just foo's downloading the mod at runtime)

        // ignore this packet if stuff was already applied, probably sent twice due to us asking the server
        netClient.addBinaryPacketHandler("flood") {
            if (applied) return@addBinaryPacketHandler

            Log.debug("Flood responded")
            enable()

            // fetch at most once every 10 minutes
            if (Time.timeSinceMillis(lastFetch) >= 600000) {
                lastFetch = Time.millis()

                // new version checking code, no longer limited to float numbers
                Http.get("$ghApi/repos/mindustry-antigrief/FloodCompat/releases", ConsT { response: Http.HttpResponse? ->
                    if (response == null) {
                        versionFail()
                        return@ConsT
                    }

                    val vars = Jval.read(response.getResultAsString()).asArray().get(0).getString("tag_name").replace("[^0-9.]".toRegex(), "")
                    if (vars.isEmpty()) {
                        versionFail()
                        return@ConsT
                    }

                    val mod = mods.getMod(this.javaClass)
                    if (mod != null) {
                        if (vars != mod.meta.version.replace("[^0-9.]".toRegex(), "")) {
                            newest = false
                            ui.chatfrag.addMessage(Strings.format("[scarlet]@", Core.bundle.get("fc-outdated")))

                            return@ConsT
                        }

                        newest = true
                        ui.showInfoFade(Strings.format("[lime]@", Core.bundle.get("fc-newest")), 5f)

                        return@ConsT
                    }

                    versionFail()
                })
            } else if (!newest) ui.chatfrag.addMessage(Strings.format("[scarlet]@", Core.bundle.get("fc-outdated")))

            // Respond to flood so it would know we're using the mod
            Core.app.post( { Call.serverBinaryPacketReliable("flood-rs", ByteArray(0)) } )
        }

        netClient.addBinaryPacketHandler("anticreep") { bytes: ByteArray ->
            if (!applied || bytes.size < 10 || Core.settings.getInt("fc-quality") == 2) return@addBinaryPacketHandler // This can eat some anticreep packets right when the player joins, but it's not a big deal

            val buffer = ByteBuffer.wrap(bytes)

            val pos = buffer.getInt()
            val time = buffer.getFloat()
            val team = (buffer.get().toInt() and 0xff)
            val rad = (buffer.get().toInt() and 0xff)

            if (pos <= 0 || rad <= 0 || time <= 0 || team <= 0) return@addBinaryPacketHandler
            val tile = world.tile(pos) ?: return@addBinaryPacketHandler
            val color = Team.get(team).color

            val tiles = Seq<Tile>()
            Geometry.circle(tile.x.toInt(), tile.y.toInt(), rad) { cx: Int, cy: Int ->
                val t = world.tile(cx, cy)
                if (t != null && !allTiles.contains(t)) {
                    tiles.add(t)
                }
            }
            allTiles.addAll(tiles)

            val startTime = Time.millis()

            allTasks.addAll(
                Timer.schedule({
                    val sizeMultiplier = 1 - (Time.millis() - startTime) / 1000f / time
                    tiles.each { t: Tile ->
                        Timer.schedule({
                            Fx.lightBlock.at(
                                t.getX(),
                                t.getY(),
                                Mathf.random(0.01f, 1.5f * sizeMultiplier),
                                color
                            )
                        }, Mathf.random(1f))
                    }
                }, 0f, 1f, time.toInt()),

                Timer.schedule({
                    allTiles.removeAll(tiles)
                    tiles.clear()
                }, time)
            )
        }

        Timer.schedule({
            val it = allTasks.iterator()
            while (it.hasNext()) {
                val task = it.next()
                if (task == null || !task.isScheduled)
                    it.remove()
            }
        }, 0f, 5f)
    }

    private fun versionFail(){
        newest = true
        ui.chatfrag.addMessage(Strings.format("[scarlet]@", Core.bundle.get("fc-fetch-fail")))
    }

    /** This is a function so that foo's can call it when downloading the mod */
    private fun onWorldLoad() {
        Log.debug("Sent flood")
        // Ask flood to resend the init packet
        Core.app.post( { Call.serverBinaryPacketReliable("flood-pr", ByteArray(0)) } )

        allTiles.clear()
        allTasks.each{ it.cancel() }
        allTasks.clear()
    }

    /** Applies flood changes */
    private fun enable() {
        if (applied) return

        applied = true
        Core.settings.put("fc-applied", true)

        Log.info("Enabling FloodCompat")
        Time.mark()

        overwrites( // This system is mostly functional and saves a lot of copy pasting.
            //Blocks
            scrapWall, "solid", false,
            titaniumWall, "solid", false,
            thoriumWall, "solid", false,
            berylliumWall, "absorbLasers", true,
            tungstenWall, "absorbLasers", true,
            carbideWall, "absorbLasers", true,
            phaseWall, "chanceDeflect", 0,
            surgeWall, "lightningChance", 0,
            reinforcedSurgeWall, "lightningChance", 0,
            radar, "health", 500,
            shockwaveTower, "health", 2000,
            thoriumReactor, "health", 1400,
            massDriver, "health", 1250,
            impactReactor, "rebuildable", false,
            *(scathe as ItemTurret).ammoTypes.flatMap { Seq.with(
                it.value, "buildingDamageMultiplier", 0.3F,
                it.value, "damage", 700,
                it.value, "splashDamage", 80
            ) }.toTypedArray(),
            parallax, "damage", 6,
            *(foreshadow as ItemTurret).ammoTypes.flatMap { Seq.with(
                it.value, "createChance", 0f,
                it.value, "damage", 560,
                it.value, "buildingDamageMultiplier", 1f
            ) }.toTypedArray(),
            *(cyclone as ItemTurret).ammoTypes.flatMap {
                if (it.key == Items.blastCompound) { Seq.with(
                    it.value, "splashDamage", 20f,
                    it.value, "ammoMultiplier", 3f,
                    it.value, "splashDamageRadius", 45f,
                    it.value, "splashDamagePierce", true
                ) }
                else {Seq.with()}}.toTypedArray(),
            *(spectre as ItemTurret).ammoTypes.flatMap {
                if (it.key == Items.thorium) { Seq.with(
                    it.value, "pierceCap", 6
                ) }
                else {Seq.with()}}.toTypedArray(),
            *(titan as ItemTurret).ammoTypes.flatMap { Seq.with(
                it.value, "splashDamagePierce", true,
                it.value, "scaledSplashDamage", false
            ) }.toTypedArray(),

            // Units
            vela, "commands", vela.commands.copy().add(UnitCommand.mineCommand),
            vela, "mineTier", 4,
            vela, "mineSpeed", 10.5F,
            pulsar, "abilities", Seq<Ability>(0),
            bryde, "abilities", Seq<Ability>(0),
            oct, "abilities", Seq<Ability>(0),
            *quad.weapons.flatMap { Seq.with(
                it, "bullet.damage", 100,
                it, "bullet.splashDamage", 250,
                it, "bullet.splashDamageRadius", 100,
            ) }.toArray(),
            *fortress.weapons.flatMap { Seq.with(
                it, "bullet.damage", 40,
                it, "bullet.splashDamageRadius", 60
            ) }.toArray(),
            *scepter.weapons.flatMap { if (it.name == "scepter-weapon") Seq.with(
                it, "bullet.pierce", true,
                it, "bullet.pierceCap", 3
            ) else Seq.with(it, "bullet.damage", 25) }.toArray(),
            *reign.weapons.flatMap { Seq.with(
                it, "bullet.damage", 120,
                it, "bullet.pierceCap", 15,
                it, "bullet.fragBullet.damage", 30,
                it, "bullet.fragBullet.pierceCap", 6
            ) }.toArray(),
            crawler, "targetAir", false,
            spiroct, "targetAir", false,
            spiroct, "speed", 0.4F,
            *spiroct.weapons.flatMap { Seq.with(it, "bullet.damage", if (it.name == "spiroct-weapon") 25 else 20 ) }.toArray(),
            arkyid, "targetAir", false,
            arkyid, "speed", 0.5F,
            arkyid, "hitSize", 21,
            *arkyid.weapons.flatMap {
                if(it.bullet is SapBulletType) Seq.with()
                else Seq.with(
                    it, "bullet.damage", 80,
                    it, "bullet.collidesAir", true,
                    it, "bullet.collidesGround", true,
                    it, "bullet.splashDamagePierce", true,
                    it, "bullet.splashDamageRadius", 20,
                    it, "bullet.splashDamage", 35,
                )
            }.toArray(),
            crawler, "health", 100,
            crawler, "speed", 1.5F,
            crawler, "accel", 0.08F,
            crawler, "drag", 0.016F,
            crawler, "hitSize", 6,
            atrax, "speed", 0.5F,
            toxopid, "hitSize", 21,
            *toxopid.weapons.flatMap<Any> { if (it.name == "toxopid-cannon") Seq.with(
                it.bullet.fragBullet, "splashDamagePierce", true,
                it.bullet.fragBullet, "splashDamageRadius", 50,
                it.bullet, "splashDamagePierce", true,
            ) else Seq.with() }.toArray(),
            flare, "health", 275,
            flare, "engineOffset", 5.5F,
            flare, "range", 140,
            horizon, "health", 440,
            horizon, "speed", 1.7F,
            horizon, "itemCapacity", 20,
            zenith, "health", 1400,
            zenith, "speed", 1.8F,
            *vela.weapons.flatMap { Seq.with(it.bullet, "damage", 20) }.toArray(),
            *oct.abilities.flatMap<Any> { if (it is ForceFieldAbility) Seq.with(
                it, "regen", 16,
                it, "max", 15_000
            ) else Seq.with() }.toArray(),
            *minke.weapons.flatMap { if (it.bullet is FlakBulletType) Seq.with(it.bullet, "collidesGround", true) else Seq.with<Any>()}.toArray(),
            *vanquish.weapons.flatMap<Any> { if (it.name == "vanquish-weapon") Seq.with(
                it.bullet, "splashDamagePierce", true,
                it.bullet.fragBullet, "splashDamagePierce", true
            ) else Seq.with() }.toArray(),
            *conquer.weapons.first().bullet.spawnBullets.flatMap<Any> { Seq.with(it, "splashDamagePierce", true) }.toArray(),
            *merui.weapons.flatMap<Any> { Seq.with(
                it.bullet, "collides", true,
                it.bullet, "splashDamagePierce", true,
                it.bullet, "damage", 20
            ) }.toArray(),
            *elude.weapons.flatMap { Seq.with(
                it.bullet, "damage", 35
            ) }.toArray(),
            *locus.weapons.flatMap { Seq.with(
                it.bullet, "pierce", true,
                it.bullet, "pierceCap", 3,
                it.bullet, "pierceDamageFactor", 0.15F
            ) }.toArray(),
            *anthicus.weapons.flatMap { Seq.with(it, "bullet.splashDamagePierce", true) }.toArray(),
            *obviate.weapons.flatMap { Seq.with(
                it.bullet, "splashDamage", 40,
                it.bullet, "splashDamageRadius", 20,
                it.bullet, "splashDamagePierce", true
            ) }.toArray(),
            *precept.weapons.flatMap { Seq.with(
                it.bullet, "pierceCap", 3
            ) }.toArray(),
            *quell.weapons.flatMap { Seq.with(
                it.bullet, "splashDamagePierce", true,
                it.bullet, "buildingDamageMultiplier", 0.5F
            ) }.toArray(),
            *disrupt.weapons.flatMap { Seq.with(
                it.bullet, "splashDamagePierce", true,
                it.bullet, "buildingDamageMultiplier", 0.5F,
                it.bullet, "splashDamageRadius", 32
            ) }.toArray(),
            *collaris.weapons.flatMap { Seq.with(
                it.bullet, "splashDamageRadius", 42
            ) }.toArray()
        )

        Log.debug("Enabled FloodCompat in ${Time.elapsed()}ms")
    }

    /** Reverts flood changes */
    private fun disable() {
        if (!applied) return

        applied = false
        Core.settings.put("fc-applied", false)

        Log.info("Disabling FloodCompat")
        Time.mark()

        defaults.indices.step(3).forEach { (defaults[it + 1] as Field).set(defaults[it], defaults[it + 2]) } // (obj, field, value) -> field.set(obj, value)
        defaults.clear()
        Log.debug("Disabled FloodCompat in ${Time.elapsed()}ms")
    }


    // Utility functions

    /** Convenient way of adding multiple overwrites at once */
    private fun overwrites(vararg args: Any) =
        args.indices.step(3).forEach { overwrite(args[it], args[it + 1] as String, args[it + 2]) }

    private fun <O : Any, T : Any> overwrite(obj: O, name: String, value: T) {
        val split = name.split('.', limit = 2)
        val field = obj::class.java.getField(split[0])
        field.isAccessible = true

        // In the case of a string with periods, run the function recursively until we get to the last item which is then set
        if (split.size > 1) return overwrite(field.get(obj), split[1], value)

        defaults.add(obj)
        defaults.add(field)
        defaults.add(field.get(obj))
        field.set(obj, value)
    }
}
