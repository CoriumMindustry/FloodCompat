package floodcompat

import arc.*
import arc.func.*
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
    /** All the tiles that currently have effects drawn on top */
    private val allTiles = ObjectSet<Tile>()
    private val allTasks = Seq<Timer.Task>()

    /** Time of the last version fetch, in millis */
    private var lastFetch = 0L
    /** Whether the mod's up to date */
    private var newest = false

    override fun init() {
        Log.info("Flood Compatibility loaded!")

        Events.on(EventType.ClientLoadEvent::class.java) {
            SettingCache.init()
            EditDrawers.init()
            SoundUtils.init()

            SettingCache.applied = false
        }

        // call onWorldLoad() - it's necessary for /sync to not disable the mod
        Events.on(EventType.WorldLoadEvent::class.java) { onWorldLoad() }

        Events.on(EventType.ResetEvent::class.java) {
            SettingCache.applied = false

            SoundUtils.setVanilla()
        }

        if (!state.isMenu)
            onWorldLoad() // Mod was initialized after loading a world (realistically just foo's downloading the mod at runtime)

        // ignore this packet if stuff was already applied, probably sent twice due to us asking the server
        netClient.addBinaryPacketHandler("flood") {
            bytes: ByteArray -> SettingCache.load(bytes)
            if (SettingCache.applied) return@addBinaryPacketHandler

            Log.debug("Flood responded")
            SettingCache.applied = true

            SoundUtils.replaceVanilla()

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
            Core.app.post( {
                var range = Core.settings.getInt("fc-culling", -1)
                if(Core.settings.getInt("fc-quality", 0) >= 2)
                    range = 40

                Call.serverBinaryPacketReliable(
                    "flood-rs",
                    byteArrayOf(
                        range.toByte()
                    )
                )
            } )
        }

        netClient.addBinaryPacketHandler("flood-ac") { bytes: ByteArray ->
            if (!SettingCache.applied || bytes.size < 10 || Core.settings.getInt("fc-quality") == 2) return@addBinaryPacketHandler // This can eat some anticreep packets right when the player joins, but it's not a big deal

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

        netClient.addBinaryPacketHandler("flood-nfx") { bytes: ByteArray ->
            if (!Core.settings.getBool("fc-customs") || SoundUtils.cachedSound == null) return@addBinaryPacketHandler

            val pos = ByteBuffer.wrap(bytes).getInt()
            val x = Point2.x(pos) * tilesize
            val y = Point2.y(pos) * tilesize

            SoundUtils.cachedSound.at(x.toFloat(), y.toFloat(), 1f, 4f)
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
}
