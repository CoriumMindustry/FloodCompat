package floodcompat

import arc.*
import arc.func.*
import arc.graphics.*
import arc.math.*
import arc.math.geom.*
import arc.struct.*
import arc.util.*
import arc.util.serialization.*
import mindustry.Vars.*
import mindustry.content.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.mod.*
import mindustry.world.*
import java.nio.*

// Based on old foo's implementation
class FloodCompat : Mod() {
    /** All anticreep instances to update */
    private val anticreeps = Seq<AnticreepState>()
    /** Tile states, for checking if they currently have effects drawn on top */
    private var tileStates = Bits()

    /** Last version fetch time, in millis */
    private var lastFetch = 0L
    /** Whether the mod is up to date */
    private var newest = false

    private var appliedModules = false

    private var version: ByteArray = byteArrayOf()

    fun initModules() {
        appliedModules = true
        SettingCache.init()
        EditDrawers.init()
        SoundUtils.init()
    }

    override fun init() {
        Log.info("Flood Compatibility loaded!")

        Events.on(EventType.ClientLoadEvent::class.java) {
            initModules()

            SettingCache.applied = false
        }

        // call onWorldLoad() - it's necessary for /sync to not disable the mod
        Events.on(EventType.WorldLoadEvent::class.java) { onWorldLoad() }

        Events.on(EventType.ResetEvent::class.java) {
            SettingCache.applied = false

            SoundUtils.setVanilla()
        }

        if (!appliedModules) {
            initModules()
            onWorldLoad() // Mod was initialized after loading a world (realistically just foo's downloading the mod at runtime)
        }

        // ignore this packet if stuff was already applied, probably sent twice due to us asking the server
        netClient.addBinaryPacketHandler("flood") { bytes: ByteArray ->
            SettingCache.load(bytes)
            if (SettingCache.applied) return@addBinaryPacketHandler

            Log.debug("Flood responded")
            SettingCache.applied = true

            SoundUtils.replaceVanilla()

            // fetch at most once every 10 minutes
            if (Time.timeSinceMillis(lastFetch) >= 600000) {
                lastFetch = Time.millis()

                // new version checking code, no longer limited to float numbers
                Http.get("$ghApi/repos/mindustry-antigrief/FloodCompat/releases") { response ->
                    if (response == null) {
                        versionFail()
                        return@get
                    }

                    val vars = Jval.read(response.getResultAsString()).asArray().get(0).getString("tag_name").replace("[^0-9.]".toRegex(), "")
                    if (vars.isEmpty()) {
                        versionFail()
                        return@get
                    }

                    val mod = mods.getMod(this.javaClass)
                    if (mod != null) {
                        if (vars != mod.meta.version.replace("[^0-9.]".toRegex(), "")) {
                            newest = false
                            ui.chatfrag.addMessage(Strings.format("[scarlet]@", Core.bundle.get("fc-outdated")))

                            return@get
                        }

                        newest = true
                        ui.showInfoFade(Strings.format("[lime]@", Core.bundle.get("fc-newest")), 5f)

                        return@get
                    }

                    versionFail()
                }
            } else if (!newest) ui.chatfrag.addMessage(Strings.format("[scarlet]@", Core.bundle.get("fc-outdated")))

            // Respond to flood so it would know we're using the mod
            Core.app.post {
                var range = Core.settings.getInt("fc-culling", -1)
                if (Core.settings.getInt("fc-quality", 0) >= 2)
                    range = 40

                Call.serverBinaryPacketReliable(
                    "flood-rs",
                    (
                        getLocalVersion() +
                        byteArrayOf(
                            range.toByte()
                        )
                    )
                )
            }

            Events.run(EventType.Trigger.update) { drawAnticreep() }
        }

        netClient.addBinaryPacketHandler("flood-ac") { bytes ->
            if (!SettingCache.applied || bytes.size < 14 || Core.settings.getInt("fc-quality") == 2) return@addBinaryPacketHandler // This can eat some anticreep packets right when the player joins, but it's not a big deal

            val buffer = ByteBuffer.wrap(bytes)

            val pos = buffer.getInt()
            val end = buffer.getDouble()
            val team = (buffer.get().toInt() and 0xff)
            val rad = (buffer.get().toInt() and 0xff)

            if (pos <= 0 || rad <= 0 || end <= 0 || team <= 0) return@addBinaryPacketHandler
            val tile = world.tile(pos) ?: return@addBinaryPacketHandler
            val color = Team.get(team).color

            val tiles = Seq<Tile>()
            Geometry.circle(tile.x.toInt(), tile.y.toInt(), rad) { cx: Int, cy: Int ->
                val t = world.tile(cx, cy)
                if (t != null && !tileStates.get(t.array())) {
                    tileStates.set(t.array(), true)
                    tiles.add(t)
                }
            }

            anticreeps.add(
                AnticreepState(
                    tiles,
                    color,
                    end
                )
            )
        }

        netClient.addBinaryPacketHandler("flood-nfx") { bytes ->
            if (!Core.settings.getBool("fc-customs") || SoundUtils.cachedSound == null) return@addBinaryPacketHandler

            val pos = ByteBuffer.wrap(bytes).getInt()
            val x = Point2.x(pos) * tilesize
            val y = Point2.y(pos) * tilesize

            SoundUtils.cachedSound.at(x.toFloat(), y.toFloat(), 1f, 4f)
        }
    }

    private fun getLocalVersion(): ByteArray {
        if (version.isEmpty()) {
            val split = mods.getMod(this.javaClass).meta.version.replace("[^0-9.]".toRegex(), "").split('.')
            val buffer = ByteBuffer.allocate(split.size + 1).put(split.size.toByte())
            for (str in split) {
                buffer.put(
                    Strings.parseInt(
                        str
                    ).toByte()
                )
            }
            version = buffer.array()
        }

        return version
    }

    private fun versionFail() {
        newest = true
        ui.chatfrag.addMessage(Strings.format("[scarlet]@", Core.bundle.get("fc-fetch-fail")))
    }

    /** This is a function so that foo's can call it when downloading the mod */
    private fun onWorldLoad() {
        Log.debug("Sent flood")
        // Ask flood to resend the init packet
        Core.app.post { Call.serverBinaryPacketReliable("flood-pr", ByteArray(0)) }

        tileStates = Bits(world.height() * world.width())
        anticreeps.clear()
    }

    class AnticreepState(val tiles: Seq<Tile>, val color: Color, val end: Double) {
        val lifetime: Float = (end - state.tick).toFloat()
        var ticks: FloatArray = FloatArray(tiles.size)
    }

    private fun drawAnticreep() {
        val it: MutableIterator<AnticreepState> = anticreeps.iterator()
        while (it.hasNext()) {
            val ac = it.next()
            if (state.tick > ac.end) {
                ac.tiles.each { t -> tileStates.set(t.array(), false) }
                it.remove()
                return
            }

            val size = (ac.end - state.tick).toFloat() / ac.lifetime
            for (i in 0 ..< ac.tiles.size) {
                ac.ticks[i] -= Time.delta
                if (ac.ticks[i] <= 0f) {
                    ac.ticks[i] = Mathf.random(60f)

                    val t: Tile = ac.tiles.get(i)
                    Fx.lightBlock.at(
                        t.getX(),
                        t.getY(),
                        Mathf.random(0.01f, 1.5f * size),
                        ac.color
                    )
                }
            }
        }
    }
}
