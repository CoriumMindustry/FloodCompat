package floodcompat

import arc.*
import arc.math.*
import arc.math.geom.*
import arc.struct.*
import arc.util.*
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

// Based on old foo's implementation
class FloodCompat : Mod() {
    /** Vanilla values of changed vars for restoration later */
    private val defaults = mutableListOf<Any>()
    /** All the tiles that currently have effects drawn on top */
    private val allTiles = ObjectSet<Tile>()
    private val allTasks = Seq<Timer.Task>()

    /** Used to prevent flood from applying twice */
    private var applied = false


    override fun init() {
        Log.info("Flood Compatibility loaded!")

        Events.on(EventType.ResetEvent::class.java) { disable() }
        Events.on(EventType.WorldLoadEvent::class.java) { onWorldLoad() }
        if (!state.isMenu) onWorldLoad() // Mod was initialized after loading a world (realistically just foo's downloading the mod at runtime)

        netClient.addPacketHandler("flood") { if (Strings.canParseInt(it)) enable() }
        netClient.addPacketHandler("anticreep") { string: String ->
            if (!applied) return@addPacketHandler // This can eat some anticreep packets right when the player joins, but it's not a big deal

            val vars = string.split(":", limit = 4)

            val pos = Strings.parseInt(vars[0])
            val rad = Strings.parseInt(vars[1])
            val time = Strings.parseInt(vars[2])
            val team = Strings.parseInt(vars[3])

            if (pos <= 0 || rad <= 0 || time <= 0 || team <= 0) return@addPacketHandler
            val tile = world.tile(pos) ?: return@addPacketHandler
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
                }, 0f, 1f, time),

                Timer.schedule({
                    allTiles.removeAll(tiles)
                    tiles.clear()
                }, time.toFloat())
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

    /** This is a function so that foo's can call it when downloading the mod */
    private fun onWorldLoad() {
        Log.debug("Send flood")
        Call.serverPacketReliable("flood", "1.2")

        allTiles.clear()
        allTasks.each{ it.cancel() }
        allTasks.clear()
    }

    /** Applies flood changes */
    private fun enable() {
        if (applied) throw AssertionError("Tried to enable flood even though it was already enabled!")
        applied = true

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
            *(fuse as ItemTurret).ammoTypes.flatMap { Seq.with(it.value, "pierce", false) }.toTypedArray(),
            (fuse as ItemTurret).ammoTypes.get(Items.titanium), "damage", 10,
            (fuse as ItemTurret).ammoTypes.get(Items.thorium), "damage", 20,
            *(scathe as ItemTurret).ammoTypes.flatMap { Seq.with(
                it.value, "buildingDamageMultiplier", 0.3F,
                it.value, "damage", 700,
                it.value, "splashDamage", 80
            ) }.toTypedArray(),
            parallax, "force", 8,
            parallax, "scaledForce", 7,
            parallax, "range", 230,
            parallax, "damage", 6,
            *(foreshadow as ItemTurret).ammoTypes.flatMap { Seq.with(
                it.value, "createChance", 0f,
                it.value, "damage", 560,
                it.value, "buildingDamageMultiplier", 1f
            ) }.toTypedArray(),

            // Units
            pulsar, "commands", arrayOf(UnitCommand.moveCommand, UnitCommand.boostCommand, UnitCommand.mineCommand),
            quasar, "commands", arrayOf(UnitCommand.moveCommand, UnitCommand.boostCommand, UnitCommand.mineCommand),
            pulsar, "abilities", Seq<Ability>(0), // pulsar.abilities.clear()
            bryde, "abilities", Seq<Ability>(0), // pulsar.abilities.clear()
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
                it, "bullet.pierceCap", 8
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
            *spiroct.weapons.flatMap { Seq.with(it, "bullet.damage", if (it.name == "spiroct-weapon") 25 else 20 ).apply { if (it.bullet is SapBulletType) this.add(it, "bullet.sapStrength", 0) } }.toArray(),
            arkyid, "targetAir", false,
            arkyid, "speed", 0.5F,
            arkyid, "hitSize", 21,
            *arkyid.weapons.flatMap {
                if (it.bullet is SapBulletType) Seq.with(it, "bullet.sapStrength", 0)
                else Seq.with(
                    it, "bullet.collidesAir", true,
                    it, "bullet.collidesGround", true,
                    it, "bullet.splashDamagePierce", true,
                    it, "bullet.splashDamageRadius", 20,
                    it, "bullet.splashDamage", 15,
                    it, "bullet.lightning", 0,
                    it, "bullet.buildingDamageMultiplier", 0.01F
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
                it.bullet.fragBullet, "pierce", true, // TODO: Make all of the other bullet references it.bullet, "blah" instead of it, "bullet.blah"
                it.bullet.fragBullet, "pierceCap", 2
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
                it.bullet, "splashDamagePierce", true
            ) }.toArray(),
            *anthicus.weapons.flatMap { Seq.with(it, "bullet.splashDamagePierce", true) }.toArray(),
            *quell.weapons.flatMap { Seq.with(
                it.bullet, "splashDamagePierce", true,
                it.bullet, "buildingDamageMultiplier", 0.5F
            ) }.toArray(),
            *disrupt.weapons.flatMap { Seq.with(
                it.bullet, "splashDamagePierce", true,
                it.bullet, "buildingDamageMultiplier", 0.5F
            ) }.toArray()
        )

        Log.debug("Enabled FloodCompat in ${Time.elapsed()}ms")
    }

    /** Reverts flood changes */
    private fun disable() {
        if (!applied) return
        applied = false

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