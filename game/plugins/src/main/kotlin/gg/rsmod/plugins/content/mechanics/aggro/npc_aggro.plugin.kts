package gg.rsmod.plugins.content.mechanics.aggro

import gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR
import gg.rsmod.plugins.content.combat.getCombatTarget
import gg.rsmod.plugins.content.combat.isAttacking
import gg.rsmod.plugins.content.combat.isBeingAttacked

val AGGRO_CHECK_TIMER = TimerKey()

val defaultAggressiveness: (Npc, Player) -> Boolean = boolean@{ n, p ->
    if (n.combatDef.aggressiveTimer == Int.MAX_VALUE) {
        return@boolean true
    } else if (n.combatDef.aggressiveTimer == Int.MIN_VALUE) {
        return@boolean false
    }

    if (Math.abs(world.currentCycle - p.lastMapBuildTime) > n.combatDef.aggressiveTimer) {
        return@boolean false
    }

    val npcLvl = n.def.combatLevel
    return@boolean p.combatLevel <= npcLvl * 2
}

on_global_npc_spawn {
    if (npc.combatDef.aggressiveRadius > 0) {
        npc.aggroCheck = defaultAggressiveness
        npc.timers[AGGRO_CHECK_TIMER] = 1
    }
}

on_timer(AGGRO_CHECK_TIMER) {
    if ((!npc.isAttacking() || npc.tile.isMulti(world)) && npc.lock.canAttack() && npc.isActive()) {
        if (!checkRadius(npc)) {
            npc.stopMovement()
            npc.resetInteractions()
            npc.resetFacePawn()
            npc.interruptQueues()
        }
    }
    npc.timers[AGGRO_CHECK_TIMER] = if (npc.combatDef.aggroTargetDelay <= 0) world.random(1..10) else npc.combatDef.aggroTargetDelay
}

fun checkRadius(npc: Npc): Boolean {
    val radius = npc.combatDef.aggressiveRadius
    for (x in -radius..radius) {
        for (z in -radius..radius) {
            val tile = npc.tile.transform(x, z)
            val chunk = world.chunks.get(tile, createIfNeeded = false) ?: continue

            val players = chunk.getEntities<Player>(tile, EntityType.PLAYER, EntityType.CLIENT)
            if (players.isEmpty()) {
                continue
            }

            val targets = players.filter { canAttack(npc, it) }
            if (targets.isEmpty()) {
                continue
            }

            val target = targets.random()
            if (npc.getCombatTarget() != target) {
                if (!target.isAttacking() && !target.isBeingAttacked()) {
                    npc.attack(target)
                    return true
                }
            }
        }
    }
    return false
}

fun canAttack(npc: Npc, target: Player): Boolean {
    if (!target.isOnline || target.invisible || target.attr.has(COMBAT_TARGET_FOCUS_ATTR)) {
        return false
    }
    return npc.aggroCheck == null || npc.aggroCheck?.invoke(npc, target) == true
}