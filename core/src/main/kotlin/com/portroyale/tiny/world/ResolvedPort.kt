package com.portroyale.tiny.world

import com.badlogic.gdx.math.Vector2

/** A port placed on the map: [worldPos] is where its town is drawn, [approachPoint] the water a
 *  hull actually sails to when it calls there. */
class ResolvedPort(val port: Ports.Port, val worldPos: Vector2, val approachPoint: Vector2)

/** Every port in [Ports.ALL] placed on [worldMap], approached from the water nearest its projected
 *  spot (or the spot itself if no water is found). A function rather than a step inside the
 *  game's create() so a test can place the ports exactly as the game does. */
fun resolvePorts(worldMap: WorldMap): List<ResolvedPort> = Ports.ALL.map { port ->
    val pos = MapProjection.project(port.lat, port.lon)
    val approach = worldMap.findNearestWater(pos.x, pos.y) ?: pos
    ResolvedPort(port, pos, approach)
}
